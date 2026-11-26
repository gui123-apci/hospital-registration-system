package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.common.enums.OrderStatus;
import com.hospital.common.enums.SourceStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.report.ReportCreateDTO;
import com.hospital.entity.Doctor;
import com.hospital.entity.NumberSource;
import com.hospital.entity.Order;
import com.hospital.entity.Patient;
import com.hospital.entity.Report;
import com.hospital.entity.Schedule;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.NumberSourceMapper;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.ReportMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.service.DoctorWorkService;
import com.hospital.util.SlotTimeUtil;
import com.hospital.vo.doctor.WaitingVO;
import com.hospital.vo.order.OrderVO;
import com.hospital.vo.report.ReportVO;
import com.hospital.vo.schedule.ScheduleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 医生工作台实现：叫号 + 开报告。
 *
 * 两个动作都先走"医生归属"校验：账号 → tb_doctor.userId → 医生 → 排班.doctorId，
 * 保证医生只能操作自己排班下的单，不能给别人排班的患者开报告/叫号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorWorkServiceImpl implements DoctorWorkService {

    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final OrderMapper orderMapper;
    private final NumberSourceMapper numberSourceMapper;
    private final PatientMapper patientMapper;
    private final ReportMapper reportMapper;

    /**
     * 医生归属校验：账号 → tb_doctor.user_id → 医生 → 排班.doctor_id。
     * 三个动作（候诊队列、叫号、开报告）共用，防止医生操作别人排班下的单。
     */
    private Schedule checkOwnership(Long scheduleId, Long doctorUserId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }
        Doctor doctor = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getUserId, doctorUserId));
        if (doctor == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_BOUND);
        }
        if (!schedule.getDoctorId().equals(doctor.getId())) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_OWNED);
        }
        return schedule;
    }

    @Override
    public List<ScheduleVO> listMySchedules(Long doctorUserId) {
        Doctor doctor = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getUserId, doctorUserId));
        if (doctor == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_BOUND);
        }

        // 只多取一天之后的数据没意义：工作台只关心"还能出诊的时段"，
        // 今天已结束的时段（如 12:00 后的上午）不再出现，避免医生对着空队列操作
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctor.getId())
                .ge(Schedule::getWorkDate, LocalDate.now())
                .orderByAsc(Schedule::getWorkDate)
                .orderByAsc(Schedule::getTimeSlot));
        return schedules.stream()
                .filter(schedule -> !SlotTimeUtil.isSlotEnded(schedule.getWorkDate(), schedule.getTimeSlot()))
                .map(ScheduleVO::from)
                .toList();
    }

    @Override
    public List<WaitingVO> listWaiting(Long scheduleId, Long doctorUserId) {
        checkOwnership(scheduleId, doctorUserId);

        // 与叫号用同一套队列定义：已支付 + 已签到，按签到时间升序——只是这里取全部
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getScheduleId, scheduleId)
                .eq(Order::getStatus, OrderStatus.PAID.getCode())
                .isNotNull(Order::getSignTime)
                .orderByAsc(Order::getSignTime));
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量取姓名，避免逐条查库的 N+1
        Set<Long> patientIds = orders.stream().map(Order::getPatientId).collect(Collectors.toSet());
        Map<Long, String> nameMap = patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getName, (a, b) -> a));

        List<WaitingVO> result = new ArrayList<>(orders.size());
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            WaitingVO vo = new WaitingVO();
            vo.setSeq(i + 1);
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setPatientName(nameMap.get(order.getPatientId()));
            vo.setSignTime(order.getSignTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO callNext(Long scheduleId, Long doctorUserId) {
        Schedule schedule = checkOwnership(scheduleId, doctorUserId);

        // 叫号队列：已支付 + 已签到，按签到时间升序取最早候诊的那位。
        // 没签到的人不进队列——他们连"到场"都没证明，不该被叫到
        Order next = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getScheduleId, scheduleId)
                .eq(Order::getStatus, OrderStatus.PAID.getCode())
                .isNotNull(Order::getSignTime)
                .orderByAsc(Order::getSignTime)
                .last("LIMIT 1"));
        if (next == null) {
            throw new BusinessException(ResultCode.NO_WAITING_PATIENT);
        }

        // 抢状态权：订单 1→6（就诊中）。连点两次叫号，第二次时这条单已不是 status=1，
        // 队列会取到下一位——天然前进，同一个患者不会被叫两遍
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, next.getId())
                .eq(Order::getStatus, OrderStatus.PAID.getCode())
                .set(Order::getStatus, OrderStatus.IN_PROGRESS.getCode()));
        if (orderRows == 0) {
            // 被并发叫走，本轮无号可叫
            throw new BusinessException(ResultCode.NO_WAITING_PATIENT);
        }

        // 号源 2→3，必须查行数：状态不符就抛异常回滚，让订单和号源始终同进退
        int sourceRows = numberSourceMapper.update(null, new LambdaUpdateWrapper<NumberSource>()
                .eq(NumberSource::getId, next.getSourceId())
                .eq(NumberSource::getStatus, SourceStatus.SOLD.getCode())
                .set(NumberSource::getStatus, SourceStatus.VISITED.getCode()));
        if (sourceRows == 0) {
            log.error("叫号号源状态变更失败(与预期不符), orderId={}, sourceId={}",
                    next.getId(), next.getSourceId());
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        Patient patient = patientMapper.selectById(next.getPatientId());
        next.setStatus(OrderStatus.IN_PROGRESS.getCode());
        return OrderVO.from(next, patient, schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO completeConsultation(Long orderId, Long doctorUserId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        // 医生归属校验：只能完成自己排班下的就诊
        Schedule schedule = checkOwnership(order.getScheduleId(), doctorUserId);

        // 就诊中 → 已就诊，条件更新防并发重复完成
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.IN_PROGRESS.getCode())
                .set(Order::getStatus, OrderStatus.FINISHED.getCode()));
        if (rows == 0) {
            // 已被人完成、或状态不是就诊中，都按"状态不允许"处理
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        Patient patient = patientMapper.selectById(order.getPatientId());
        order.setStatus(OrderStatus.FINISHED.getCode());
        return OrderVO.from(order, patient, schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO createReport(ReportCreateDTO dto, Long doctorUserId) {
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        // 报告只能长在"开始就诊"的订单上（就诊中/已就诊）：没叫到的单开报告，
        // 等于给没就诊的人造记录
        boolean visiting = Integer.valueOf(OrderStatus.IN_PROGRESS.getCode()).equals(order.getStatus());
        boolean finished = Integer.valueOf(OrderStatus.FINISHED.getCode()).equals(order.getStatus());
        if (!(visiting || finished)) {
            throw new BusinessException(ResultCode.REPORT_NOT_ALLOWED);
        }

        Schedule schedule = scheduleMapper.selectById(order.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }
        Doctor doctor = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getUserId, doctorUserId));
        if (doctor == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_BOUND);
        }
        // 归属校验：只能给自己的排班患者的订单开报告
        if (!schedule.getDoctorId().equals(doctor.getId())) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_OWNED);
        }

        Report report = new Report();
        report.setPatientId(order.getPatientId());
        report.setOrderId(order.getId());
        report.setDoctorId(doctor.getId());
        report.setReportType(dto.getReportType());
        report.setTitle(dto.getTitle());
        report.setContent(dto.getContent());
        report.setReportTime(LocalDateTime.now());
        reportMapper.insert(report);

        Patient patient = patientMapper.selectById(order.getPatientId());
        return ReportVO.from(report, patient != null ? patient.getName() : null);
    }
}
