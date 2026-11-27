package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.common.enums.OrderStatus;
import com.hospital.common.enums.NotificationType;
import com.hospital.common.enums.PaymentChannel;
import com.hospital.common.enums.PaymentStatus;
import com.hospital.common.enums.SourceStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.payment.PayDTO;
import com.hospital.dto.payment.RefundDTO;
import com.hospital.entity.NumberSource;
import com.hospital.entity.Order;
import com.hospital.entity.Payment;
import com.hospital.entity.Schedule;
import com.hospital.mapper.NumberSourceMapper;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.PaymentMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.service.NotificationService;
import com.hospital.service.PaymentService;
import com.hospital.util.SlotTimeUtil;
import com.hospital.util.StockCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付 + 退号实现。
 *
 * 所有"改状态"都走条件 UPDATE（WHERE status=旧值），把"判断状态对不对"和"改状态"焊成一步：
 * 并发双击支付、退号与超时释放同时打到同一单时，只有一个能改成功，另一个影响 0 行报"状态不允许"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final NumberSourceMapper numberSourceMapper;
    private final ScheduleMapper scheduleMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(PayDTO dto, Long userId) {
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        // 支付也要校验归属：下单时校验过就诊人，但订单是资产，不能让别人替自己付
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_OWNED);
        }
        if (!Integer.valueOf(OrderStatus.WAIT_PAY.getCode()).equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
        // 超时未支付（定时任务还没扫到）时主动来付，直接拒绝，让用户重新挂号
        if (order.getDeadlineTime() != null && order.getDeadlineTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.ORDER_TIMEOUT);
        }

        // 条件 UPDATE 抢"支付权"：只有 status=0 才改成功，防并发重复支付
        // TODO 当前是纯模拟支付：channel 只记流水，传微信/支付宝也立刻成功。
        //  真实支付要在支付网关回调里改订单状态，这里只能创建"待支付"流水（M5 之后的事）
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.WAIT_PAY.getCode())
                .set(Order::getStatus, OrderStatus.PAID.getCode())
                .set(Order::getPayTime, now));
        if (rows == 0) {
            // 抢失败最常见的原因：用户点支付的瞬间定时任务把单超时释放了。
            // 重查一次状态分流提示：已取消 → "订单已超时"，其他 → 通用状态错误
            Order latest = orderMapper.selectById(order.getId());
            if (latest != null && Integer.valueOf(OrderStatus.CANCELED.getCode()).equals(latest.getStatus())) {
                throw new BusinessException(ResultCode.ORDER_TIMEOUT);
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        // 号源：锁定(1) → 已售出(2)
        int sourceRows = numberSourceMapper.update(null, new LambdaUpdateWrapper<NumberSource>()
                .eq(NumberSource::getId, order.getSourceId())
                .eq(NumberSource::getStatus, SourceStatus.LOCKED.getCode())
                .set(NumberSource::getStatus, SourceStatus.SOLD.getCode()));
        if (sourceRows == 0) {
            log.warn("支付时号源状态异常, orderId={}, sourceId={}", order.getId(), order.getSourceId());
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        // 落支付单
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setUserId(order.getUserId());
        payment.setPatientId(order.getPatientId());
        payment.setAmount(order.getAmount());
        payment.setChannel(dto.getChannel() == null ? PaymentChannel.MOCK.getCode() : dto.getChannel());
        payment.setStatus(PaymentStatus.SUCCESS.getCode());
        payment.setPayTime(now);
        paymentMapper.insert(payment);

        log.info("支付成功, orderId={}, amount={}", order.getId(), order.getAmount());

        // 站内通知：提醒按时就诊。发送失败不影响支付（Service 内部已吞异常）
        notificationService.send(order.getUserId(), NotificationType.PAY_SUCCESS, "支付成功",
                "挂号费 ¥" + order.getAmount() + " 已支付，请于就诊当天到院签到候诊。");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(RefundDTO dto, Long userId) {
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_OWNED);
        }
        // 只退已支付的单：待支付的单走"取消/超时释放"，不在这里退款
        if (!Integer.valueOf(OrderStatus.PAID.getCode()).equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
        // 退号时间窗口：就诊时段已开始/已过的不能退——退了号源也回可用，但 create 的
        // isExpired 会拦住新挂号，结果是 left_num 永久虚高，数据库和 Redis 都是脏账
        Schedule schedule = scheduleMapper.selectById(order.getScheduleId());
        if (schedule == null || SlotTimeUtil.isExpired(schedule.getWorkDate(), schedule.getTimeSlot())) {
            throw new BusinessException(ResultCode.REFUND_NOT_ALLOWED);
        }

        // 加锁顺序必须和下单一致：schedule → number_source → order。
        // 下单是「条件 UPDATE 扣库存（锁 schedule 行）→ 抢号源（锁 number_source 行）→ INSERT 订单」，
        // 退号原本是「锁订单 → 锁号源 → 锁库存」，两条路径方向相反，高并发下 A 持 schedule 等 source、
        // B 持 source 等 schedule，直接死锁（压测实测：50 VU 时退号死锁 40 单，订单卡在已支付）。
        //
        // 先回补库存：与下单第一步锁同一行，把冲突收口到最前面，后续步骤不再交叉等待。
        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, order.getScheduleId())
                .setSql("left_num = left_num + 1"));

        // 号源：已售出(2) → 可用(0)。0 行说明状态与预期不符（脏数据），
        // 抛异常回滚整个退号——库存回补和号让位一起撤销，比账不平好得多
        int sourceRows = numberSourceMapper.update(null, new LambdaUpdateWrapper<NumberSource>()
                .eq(NumberSource::getId, order.getSourceId())
                .eq(NumberSource::getStatus, SourceStatus.SOLD.getCode())
                .set(NumberSource::getStatus, SourceStatus.AVAILABLE.getCode()));
        if (sourceRows == 0) {
            log.error("退号时号源状态异常, orderId={}, sourceId={}", order.getId(), order.getSourceId());
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        // 最后条件 UPDATE 抢"退号权"+ 唯一键让位：active_flag 置 NULL，同一就诊人可再挂同一排班。
        // rows == 0 说明订单已被并发退掉，抛异常把前面两步一并回滚
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.PAID.getCode())
                .set(Order::getStatus, OrderStatus.REFUNDED.getCode())
                .set(Order::getActiveFlag, null));
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        // 支付单退款：成功(1) → 退款(3)（一个订单只有一条支付单，uk_order_id 保证）
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, PaymentStatus.SUCCESS.getCode()));
        if (payment != null) {
            paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                    .eq(Payment::getId, payment.getId())
                    .eq(Payment::getStatus, PaymentStatus.SUCCESS.getCode())
                    .set(Payment::getStatus, PaymentStatus.REFUNDED.getCode()));
        } else {
            // 理论不可能：支付成功必落支付单。真出现是脏数据，只记日志不打断退号主流程
            log.warn("退号时未找到成功支付单, orderId={}", order.getId());
        }

        // Redis 缓存作废：事务提交后再删（StockCacheUtil 内部注册 afterCommit），
        // 避免并发请求在提交前回填旧值导致少卖
        StockCacheUtil.evictAfterCommit(stringRedisTemplate, order.getScheduleId());

        log.info("退号成功, orderId={}, amount={}", order.getId(), order.getAmount());
    }
}
