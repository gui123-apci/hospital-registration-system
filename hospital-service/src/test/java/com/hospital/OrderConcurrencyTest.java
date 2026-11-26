package com.hospital;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.constant.RedisKeyConstants;
import com.hospital.common.enums.OrderStatus;
import com.hospital.common.enums.PaymentStatus;
import com.hospital.common.enums.ScheduleStatus;
import com.hospital.common.enums.SourceStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.order.OrderCreateDTO;
import com.hospital.dto.payment.PayDTO;
import com.hospital.dto.payment.RefundDTO;
import com.hospital.dto.report.ReportCreateDTO;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.NumberSource;
import com.hospital.entity.Order;
import com.hospital.entity.Patient;
import com.hospital.entity.Payment;
import com.hospital.entity.Schedule;
import com.hospital.entity.User;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.NumberSourceMapper;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PaymentMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.mapper.UserMapper;
import com.hospital.service.DoctorWorkService;
import com.hospital.service.OrderService;
import com.hospital.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单核心链路并发/非法状态集成测试，对准生产真实防线：
 * <ul>
 *   <li>挂号并发不超卖：Redis Lua 削峰 + 数据库条件 UPDATE 双保险，N 个号只能卖 N 次</li>
 *   <li>一人一单：uk_patient_schedule 唯一键，同一就诊人重复挂同一排班被拒，退号后让位可重挂</li>
 *   <li>支付 vs 超时释放并发：条件 UPDATE 抢状态权，只有一方成功，号源/库存始终一致</li>
 *   <li>非法状态操作：退号/叫号/报告/完成接诊在错误状态下被拒</li>
 * </ul>
 * 需要真实 MySQL + Redis（localhost，测试结束后清理自己造的数据）。
 */
@SpringBootTest
class OrderConcurrencyTest {

    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private DoctorWorkService doctorWorkService;

    @Autowired private UserMapper userMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private DoctorMapper doctorMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private NumberSourceMapper numberSourceMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private PaymentMapper paymentMapper;

    @Autowired private StringRedisTemplate stringRedisTemplate;

    // ---- 测试数据登记，@AfterEach 按子→父顺序清理 ----
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> patientIds = new ArrayList<>();
    private final List<Long> departmentIds = new ArrayList<>();
    private final List<Long> doctorIds = new ArrayList<>();
    private final List<Long> scheduleIds = new ArrayList<>();

    private long seq = System.nanoTime();

    private synchronized long nextSeq() {
        return ++seq;
    }

    // ==================== 测试 1：挂号并发不超卖 ====================

    @Test
    void concurrentCreateDoesNotOversell() throws Exception {
        int totalNum = 5;
        int requestCount = 15;

        long userId = createUser(0);
        long scheduleId = createSchedule(createDoctor(createDepartment(), null), totalNum);
        createNumberSources(scheduleId, totalNum);
        List<Long> patientIdsLocal = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            patientIdsLocal.add(createPatient(userId));
        }

        // 15 个不同就诊人同时抢 5 个号
        List<Throwable> errors = runConcurrently(requestCount, i -> {
            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setScheduleId(scheduleId);
            dto.setPatientId(patientIdsLocal.get(i));
            orderService.create(dto, userId);
        });

        // 恰好 5 单成功，其余 10 单全是"已约满"
        int successCount = requestCount - errors.size();
        List<Integer> failCodes = errors.stream()
                .filter(e -> e instanceof BusinessException)
                .map(e -> ((BusinessException) e).getCode())
                .toList();
        assertThat(successCount).isEqualTo(totalNum);
        assertThat(failCodes).hasSize(requestCount - totalNum);
        assertThat(failCodes).allMatch(code -> code.equals(ResultCode.SCHEDULE_SOLD_OUT.getCode()));

        // 数据库终态：库存清零、订单数=号数、锁定号源数=号数，物理上无超卖
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        assertThat(schedule.getLeftNum()).isZero();

        Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getScheduleId, scheduleId));
        assertThat(orderCount).isEqualTo(totalNum);

        Long lockedCount = numberSourceMapper.selectCount(new LambdaQueryWrapper<NumberSource>()
                .eq(NumberSource::getScheduleId, scheduleId)
                .eq(NumberSource::getStatus, SourceStatus.LOCKED.getCode()));
        assertThat(lockedCount).isEqualTo(totalNum);
    }

    // ==================== 测试 2：一人一单（重复被拒 + 退号让位） ====================

    @Test
    void duplicateBookingRejectedAndRefundReleasesSlot() {
        long userId = createUser(0);
        long patientId = createPatient(userId);
        long scheduleId = createSchedule(createDoctor(createDepartment(), null), 3);
        createNumberSources(scheduleId, 3);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setScheduleId(scheduleId);
        dto.setPatientId(patientId);

        // 首次挂号成功
        orderService.create(dto, userId);

        // 同一就诊人重复挂同一排班 → 被预查/唯一键拦下
        assertThat(expectBusinessCode(() -> orderService.create(dto, userId)))
                .isEqualTo(ResultCode.ORDER_DUPLICATE.getCode());

        // 支付后退号：active_flag 置 NULL 让位
        Order order = findActiveOrder(scheduleId, patientId);
        PayDTO pay = new PayDTO();
        pay.setOrderId(order.getId());
        paymentService.pay(pay, userId);

        RefundDTO refund = new RefundDTO();
        refund.setOrderId(order.getId());
        paymentService.refund(refund, userId);

        // 让位后同一就诊人可再次挂同一排班（不抛异常即成功）
        orderService.create(dto, userId);
        assertThat(findActiveOrder(scheduleId, patientId).getStatus())
                .isEqualTo(OrderStatus.WAIT_PAY.getCode());
    }

    // ==================== 测试 3：支付 vs 超时释放并发，只有一方成功 ====================

    @Test
    void payAndTimeoutReleaseRaceOnlyOneWins() throws Exception {
        int rounds = 5;
        for (int r = 0; r < rounds; r++) {
            long userId = createUser(0);
            long patientId = createPatient(userId);
            long scheduleId = createSchedule(createDoctor(createDepartment(), null), 1);
            createNumberSources(scheduleId, 1);

            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setScheduleId(scheduleId);
            dto.setPatientId(patientId);
            orderService.create(dto, userId);
            long orderId = findActiveOrder(scheduleId, patientId).getId();

            // 直接调 releaseExpiredOrder 模拟"定时任务恰好扫到这一单"，
            // 与用户支付撞在同一刻：谁先改成功 status=0 这一行谁赢
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            pool.execute(() -> {
                try {
                    start.await();
                    PayDTO pay = new PayDTO();
                    pay.setOrderId(orderId);
                    paymentService.pay(pay, userId);
                } catch (Throwable ignored) {
                    // 支付抢输时抛 ORDER_STATUS_ERROR / ORDER_TIMEOUT，属预期
                }
            });
            pool.execute(() -> {
                try {
                    start.await();
                    orderService.releaseExpiredOrder(orderId);
                } catch (Throwable ignored) {
                    // 释放抢输时条件 UPDATE 影响 0 行、直接返回；这里兜底防意外
                }
            });
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            // 终态互斥：要么已支付、要么已取消，绝不可能两边都生效
            Order order = orderMapper.selectById(orderId);
            boolean payWon = Integer.valueOf(OrderStatus.PAID.getCode()).equals(order.getStatus());
            boolean releaseWon = Integer.valueOf(OrderStatus.CANCELED.getCode()).equals(order.getStatus());
            assertThat(payWon ^ releaseWon)
                    .as("支付与超时释放必须只有一方生效, 实际状态=%s", order.getStatus())
                    .isTrue();

            // 状态与号源/库存/支付单三方一致
            NumberSource source = numberSourceMapper.selectById(order.getSourceId());
            Schedule schedule = scheduleMapper.selectById(scheduleId);
            if (payWon) {
                assertThat(source.getStatus()).isEqualTo(SourceStatus.SOLD.getCode());
                assertThat(schedule.getLeftNum()).isZero();
                Long payCount = paymentMapper.selectCount(new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getStatus, PaymentStatus.SUCCESS.getCode()));
                assertThat(payCount).isEqualTo(1);
            } else {
                assertThat(source.getStatus()).isEqualTo(SourceStatus.AVAILABLE.getCode());
                assertThat(schedule.getLeftNum()).isEqualTo(1);
                Long payCount = paymentMapper.selectCount(new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId));
                assertThat(payCount).isZero();
            }
        }
    }

    // ==================== 测试 4：非法状态操作被拒 ====================

    @Test
    void illegalStateOperationsRejected() {
        long patientUserId = createUser(0);
        long patientId = createPatient(patientUserId);
        long doctorUserId = createUser(2);
        long departmentId = createDepartment();
        long doctorId = createDoctor(departmentId, doctorUserId);
        long scheduleId = createSchedule(doctorId, 3);
        createNumberSources(scheduleId, 3);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setScheduleId(scheduleId);
        dto.setPatientId(patientId);
        orderService.create(dto, patientUserId);
        long orderId = findActiveOrder(scheduleId, patientId).getId();

        // 1. 退号：待支付订单不能退，只能走取消/超时释放
        RefundDTO refund = new RefundDTO();
        refund.setOrderId(orderId);
        assertThat(expectBusinessCode(() -> paymentService.refund(refund, patientUserId)))
                .isEqualTo(ResultCode.ORDER_STATUS_ERROR.getCode());

        // 2. 叫号：待支付（未支付未签到）的单不进候诊队列
        assertThat(expectBusinessCode(() -> doctorWorkService.callNext(scheduleId, doctorUserId)))
                .isEqualTo(ResultCode.NO_WAITING_PATIENT.getCode());

        // 3. 报告：未开始就诊（待支付）的单不能开报告
        ReportCreateDTO report = new ReportCreateDTO();
        report.setOrderId(orderId);
        report.setTitle("血常规");
        assertThat(expectBusinessCode(() -> doctorWorkService.createReport(report, doctorUserId)))
                .isEqualTo(ResultCode.REPORT_NOT_ALLOWED.getCode());

        // 4. 完成接诊：非"就诊中"订单不能被完成
        assertThat(expectBusinessCode(() -> doctorWorkService.completeConsultation(orderId, doctorUserId)))
                .isEqualTo(ResultCode.ORDER_STATUS_ERROR.getCode());
    }

    // ==================== 数据构造 ====================

    private long createUser(int role) {
        User user = new User();
        user.setUsername("u" + nextSeq());
        user.setPassword("x");
        user.setPhone("139" + String.format("%08d", nextSeq() % 100_000_000L));
        user.setRole(role);
        userMapper.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    private long createPatient(long userId) {
        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName("就诊人" + nextSeq());
        patient.setIdCard("ID" + nextSeq());
        patient.setRelation(0);
        patient.setPhone("138" + String.format("%08d", nextSeq() % 100_000_000L));
        patientMapper.insert(patient);
        patientIds.add(patient.getId());
        return patient.getId();
    }

    private long createDepartment() {
        Department department = new Department();
        department.setName("测试科室" + nextSeq());
        department.setDescription("并发测试用");
        departmentMapper.insert(department);
        departmentIds.add(department.getId());
        return department.getId();
    }

    private long createDoctor(long departmentId, Long userId) {
        Doctor doctor = new Doctor();
        doctor.setDepartmentId(departmentId);
        doctor.setName("测试医生" + nextSeq());
        doctor.setTitle("主任医师");
        doctor.setUserId(userId);
        doctorMapper.insert(doctor);
        doctorIds.add(doctor.getId());
        return doctor.getId();
    }

    /**
     * 建一个"明天出诊"的排班：workDate 用明天，绕开 isExpired 对运行时刻的依赖，
     * 让 create/refund 在任何时候跑都能走通。
     */
    private long createSchedule(long doctorId, int totalNum) {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(doctorId);
        schedule.setDepartmentId(doctorMapper.selectById(doctorId).getDepartmentId());
        schedule.setWorkDate(LocalDate.now().plusDays(1));
        schedule.setTimeSlot(1);
        schedule.setTotalNum(totalNum);
        schedule.setLeftNum(totalNum);
        schedule.setPrice(new BigDecimal("10.00"));
        schedule.setStatus(ScheduleStatus.AVAILABLE.getCode());
        scheduleMapper.insert(schedule);
        scheduleIds.add(schedule.getId());
        return schedule.getId();
    }

    private void createNumberSources(long scheduleId, int totalNum) {
        for (int i = 1; i <= totalNum; i++) {
            NumberSource source = new NumberSource();
            source.setScheduleId(scheduleId);
            source.setSourceNo(i);
            source.setStatus(SourceStatus.AVAILABLE.getCode());
            numberSourceMapper.insert(source);
        }
    }

    private Order findActiveOrder(long scheduleId, long patientId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getScheduleId, scheduleId)
                .eq(Order::getPatientId, patientId)
                .eq(Order::getActiveFlag, 1));
    }

    /**
     * 断言调用抛出指定业务码的 BusinessException，返回该业务码。
     * 不依赖 AssertJ 的 Throwable 断言写法，避免版本差异导致编译问题。
     */
    private static int expectBusinessCode(Executable executable) {
        try {
            executable.execute();
        } catch (BusinessException e) {
            return e.getCode();
        } catch (Throwable t) {
            throw new AssertionError("预期抛 BusinessException，实际抛：" + t.getClass().getName(), t);
        }
        throw new AssertionError("预期抛 BusinessException，实际没有异常");
    }

    /**
     * 线程池 + 双栅栏：先用 ready 等所有线程就位，再同时放行，
     * 让它们尽量在同一时刻竞争同一行锁。返回各线程抛出的异常。
     */
    private List<Throwable> runConcurrently(int taskCount, ConcurrentTask task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < taskCount; i++) {
            final int idx = i;
            pool.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run(idx);
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return errors;
    }

    @FunctionalInterface
    private interface ConcurrentTask {
        void run(int index) throws Exception;
    }

    @AfterEach
    void cleanup() {
        // 子 → 父顺序删除。订单/支付/号源按 scheduleId 反查，不依赖中间登记的 id，
        // 断言中途失败也能清干净，不会给下一轮留脏数据
        if (!scheduleIds.isEmpty()) {
            List<Long> orderIds = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                            .in(Order::getScheduleId, scheduleIds)
                            .select(Order::getId))
                    .stream().map(Order::getId).toList();
            if (!orderIds.isEmpty()) {
                paymentMapper.delete(new LambdaQueryWrapper<Payment>()
                        .in(Payment::getOrderId, orderIds));
                orderMapper.deleteBatchIds(orderIds);
            }
            numberSourceMapper.delete(new LambdaQueryWrapper<NumberSource>()
                    .in(NumberSource::getScheduleId, scheduleIds));
            scheduleMapper.deleteBatchIds(scheduleIds);
        }
        if (!doctorIds.isEmpty()) doctorMapper.deleteBatchIds(doctorIds);
        if (!patientIds.isEmpty()) patientMapper.deleteBatchIds(patientIds);
        if (!departmentIds.isEmpty()) departmentMapper.deleteBatchIds(departmentIds);
        if (!userIds.isEmpty()) userMapper.deleteBatchIds(userIds);
        // 清 Redis 库存 key，避免跨测试/跨轮次残留脏库存
        scheduleIds.forEach(id -> stringRedisTemplate.delete(RedisKeyConstants.STOCK_PREFIX + id));
    }
}
