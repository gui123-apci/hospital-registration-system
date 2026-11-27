package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.constant.OrderConstants;
import com.hospital.common.constant.RedisKeyConstants;
import com.hospital.common.enums.OrderStatus;
import com.hospital.common.enums.ScheduleStatus;
import com.hospital.common.enums.SourceStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.PageResult;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.order.OrderCreateDTO;
import com.hospital.entity.NumberSource;
import com.hospital.entity.Order;
import com.hospital.entity.Patient;
import com.hospital.entity.Schedule;
import com.hospital.mapper.NumberSourceMapper;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.service.OrderService;
import com.hospital.util.RedisIdWorker;
import com.hospital.util.SlotTimeUtil;
import com.hospital.util.StockCacheUtil;
import com.hospital.vo.order.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 挂号下单实现。
 *
 * 分层防线：
 * Redis 削峰（Lua 原子扣减，挡住绝大多数失败请求，不碰数据库）；
 * 数据库保对（条件 UPDATE + 唯一键，就算 Redis 多放人进来也物理杜绝超卖）。
 * Redis 不可用时自动降级直连数据库——慢，但依然对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final PatientMapper patientMapper;
    private final ScheduleMapper scheduleMapper;
    private final NumberSourceMapper numberSourceMapper;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final DefaultRedisScript<Long> stockRepayScript;
    private final RedisIdWorker redisIdWorker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(OrderCreateDTO dto, Long userId) {
        // 0. 请求级幂等：同一 requestId 只处理一次。
        //    挡的是"网络超时后用户重试"——这种重试可能已经换了就诊人或排班，
        //    uk_patient_schedule 唯一键挡不住，只能靠请求号识别
        String idempotentKey = buildIdempotentKey(userId, dto.getRequestId());
        boolean idempotentAcquired = false;
        if (idempotentKey != null) {
            try {
                Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(idempotentKey,
                        RedisKeyConstants.IDEMPOTENT_PENDING, RedisKeyConstants.IDEMPOTENT_TTL);
                if (Boolean.FALSE.equals(first)) {
                    // 重复请求：已成功就返回同一张单，还在处理中则提示稍候
                    return replayIdempotent(idempotentKey, userId);
                }
                idempotentAcquired = true;
            } catch (DataAccessException e) {
                // Redis 挂了不阻塞下单：幂等只是体验优化，唯一键仍是最终防线
                log.warn("Redis 不可用，跳过下单幂等校验, userId={}", userId);
            }
        }

        OrderVO result;
        try {
            result = doCreate(dto, userId);
        } catch (RuntimeException e) {
            // 业务失败立刻释放占位，允许用户拿同一个 requestId 重试
            releaseIdempotent(idempotentKey, idempotentAcquired);
            throw e;
        }
        // 事务提交后再回写订单号：否则并发重试会读到尚未提交的订单
        markIdempotentAfterCommit(idempotentKey, idempotentAcquired, result.getId());
        return result;
    }

    /** 下单主流程：校验 → Redis 削峰 → 数据库兜底落库 */
    private OrderVO doCreate(OrderCreateDTO dto, Long userId) {
        // 1. 就诊人归属校验：只能给自己名下的就诊人挂号
        Patient patient = patientMapper.selectById(dto.getPatientId());
        if (patient == null) {
            throw new BusinessException(ResultCode.PATIENT_NOT_FOUND);
        }
        if (!patient.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PATIENT_NOT_OWNED);
        }

        // 2. 排班可预约校验：存在、未停诊、时段未过期
        Schedule schedule = scheduleMapper.selectById(dto.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }
        Integer currentStatus = schedule.getStatus();
        if (currentStatus == null || currentStatus != ScheduleStatus.AVAILABLE.getCode()) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_AVAILABLE);
        }
        if (SlotTimeUtil.isExpired(schedule.getWorkDate(), schedule.getTimeSlot())) {
            throw new BusinessException(ResultCode.SCHEDULE_SLOT_EXPIRED);
        }

        // 3. 一人一单预查：给可读提示；真正兜底靠 uk_patient_schedule 唯一键。
        //    只数 active_flag=1 的有效单：退号/超时的历史单已置 NULL 让位，不能挡住重新挂号
        Long exist = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getPatientId, dto.getPatientId())
                .eq(Order::getScheduleId, dto.getScheduleId())
                .eq(Order::getActiveFlag, 1));
        if (exist != null && exist > 0) {
            throw new BusinessException(ResultCode.ORDER_DUPLICATE);
        }

        // 4. Redis 库存：懒加载回填（SETNX，key 不存在才写）+ Lua 原子扣减。
        //    Redis 不可用时降级：跳过削峰直连数据库，条件 UPDATE 自己能防超卖
        String stockKey = RedisKeyConstants.STOCK_PREFIX + dto.getScheduleId();
        boolean redisDeducted = false;
        try {
            // 懒加载回填 + TTL：key 到期自动消失，下次回填数据库真值——偏差最多存活 1 小时，自愈
            stringRedisTemplate.opsForValue().setIfAbsent(stockKey,
                    String.valueOf(schedule.getLeftNum()), RedisKeyConstants.STOCK_KEY_TTL);
            Long remain = stringRedisTemplate.execute(stockDeductScript, List.of(stockKey));
            if (remain != null && remain >= 0) {
                redisDeducted = true;
            } else if (remain != null && remain == -1) {
                // Lua 判定库存不足，快速失败，不碰数据库
                log.warn("Redis 库存不足快速失败, scheduleId={}, patientId={}", dto.getScheduleId(), dto.getPatientId());
                throw new BusinessException(ResultCode.SCHEDULE_SOLD_OUT);
            } else if (remain != null) {
                // -2：缓存缺失（key 被删/TTL 过期/Redis 重启），库里可能还有号，走数据库兜底
                log.warn("Redis 缓存缺失，降级走数据库兜底, scheduleId={}", dto.getScheduleId());
            } else {
                // Lua 无返回值（异常情况）：记日志，继续走数据库兜底
                log.warn("Lua 扣减无返回值，降级走数据库兜底, scheduleId={}", dto.getScheduleId());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，挂号降级直连数据库, scheduleId={}", dto.getScheduleId(), e);
        }

        // 5. 数据库兜底落库。Redis 只削峰，正确性靠数据库；
        //    万一 Redis 与库不一致多放人进来，下面的条件 UPDATE 会拦下。
        try {
            return doCreateOrder(dto, schedule, patient);
        } catch (RuntimeException e) {
            repayStock(stockKey, redisDeducted);
            throw e;
        }
    }

    /** 拼幂等 key；没传 requestId 返回 null 表示跳过幂等 */
    private String buildIdempotentKey(Long userId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return RedisKeyConstants.IDEMPOTENT_PREFIX + userId + ":" + requestId.trim();
    }

    /**
     * 重复请求的回放：占位中 → 提示稍候；已成功 → 返回同一张单。
     * 订单查不到（事务回滚 / 被清理）时释放 key，让用户能重试。
     */
    private OrderVO replayIdempotent(String key, Long userId) {
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            // 读到一半 Redis 挂了：宁可让用户重试，也不冒重复下单的险
            throw new BusinessException(ResultCode.ORDER_REPEAT_SUBMIT);
        }
        if (value == null || RedisKeyConstants.IDEMPOTENT_PENDING.equals(value)) {
            throw new BusinessException(ResultCode.ORDER_REPEAT_SUBMIT);
        }
        Order order;
        try {
            order = orderMapper.selectById(Long.valueOf(value));
        } catch (NumberFormatException e) {
            order = null;
        }
        if (order == null || !order.getUserId().equals(userId)) {
            stringRedisTemplate.delete(key);
            throw new BusinessException(ResultCode.ORDER_REPEAT_SUBMIT);
        }
        log.info("命中下单幂等，直接返回原订单, userId={}, orderId={}", userId, order.getId());
        return OrderVO.from(order,
                patientMapper.selectById(order.getPatientId()),
                scheduleMapper.selectById(order.getScheduleId()));
    }

    /** 释放幂等占位（业务失败时调用），允许用同一 requestId 重试 */
    private void releaseIdempotent(String key, boolean acquired) {
        if (key == null || !acquired) {
            return;
        }
        try {
            stringRedisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("释放幂等占位失败（TTL 会自动清理）, key={}", key, e);
        }
    }

    /**
     * 事务提交后再把订单号写回幂等 key。
     * 提前写会出现"重试读到 orderId，但订单还没提交"的假阴性，进而误删 key、第三次重试又下单。
     */
    private void markIdempotentAfterCommit(String key, boolean acquired, Long orderId) {
        if (key == null || !acquired) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeIdempotent(key, orderId);
                }

                @Override
                public void afterCompletion(int status) {
                    // 回滚兜底：catch 已释放过，这里防事务被容器标记回滚（如超时）时占位残留
                    if (status != STATUS_COMMITTED) {
                        releaseIdempotent(key, true);
                    }
                }
            });
        } else {
            writeIdempotent(key, orderId);
        }
    }

    private void writeIdempotent(String key, Long orderId) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(key, String.valueOf(orderId), RedisKeyConstants.IDEMPOTENT_TTL);
        } catch (DataAccessException e) {
            log.warn("回写幂等结果失败（TTL 会自动清理）, key={}", key, e);
        }
    }

    @Override
    public OrderVO getMine(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        // 归属校验：id 是连续自增的，不校验就等于谁都能读别人的就诊信息
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_OWNED);
        }
        return OrderVO.from(order,
                patientMapper.selectById(order.getPatientId()),
                scheduleMapper.selectById(order.getScheduleId()));
    }

    @Override
    public PageResult<OrderVO> pageMine(int pageNum, int pageSize, Integer status, Long userId) {
        // 分页参数钳制：与科室/医生/排班的 page() 保持一致
        long pn = Math.max(pageNum, 1);
        long ps = Math.min(Math.max(pageSize, 1), 100);
        Page<Order> page = orderMapper.selectPage(new Page<>(pn, ps), new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getId));
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
        // 批量查就诊人和排班，避免 N+1
        Map<Long, Patient> patientMap = patientMapper.selectBatchIds(
                        page.getRecords().stream().map(Order::getPatientId).distinct().toList()).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, Schedule> scheduleMap = scheduleMapper.selectBatchIds(
                        page.getRecords().stream().map(Order::getScheduleId).distinct().toList()).stream()
                .collect(Collectors.toMap(Schedule::getId, Function.identity()));
        List<OrderVO> vos = page.getRecords().stream()
                .map(o -> OrderVO.from(o, patientMap.get(o.getPatientId()), scheduleMap.get(o.getScheduleId())))
                .toList();
        return new PageResult<>(page.getTotal(), vos);
    }

    /**
     * 数据库事务内的落库动作：条件扣库存 → 抢号源（FOR UPDATE）→ 插订单。
     * 放在 create 的事务边界内执行（AOP 拦的是 create 方法，本私有方法共享同一事务）。
     */
    private OrderVO doCreateOrder(OrderCreateDTO dto, Schedule schedule, Patient patient) {
        // 条件 UPDATE 扣库存：WHERE left_num > 0，行锁排队 + 判断内嵌，天然防超卖
        int rows = scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, dto.getScheduleId())
                .gt(Schedule::getLeftNum, 0)
                .setSql("left_num = left_num - 1"));
        if (rows == 0) {
            log.warn("数据库库存扣减失败(已约满), scheduleId={}, patientId={}", dto.getScheduleId(), dto.getPatientId());
            throw new BusinessException(ResultCode.SCHEDULE_SOLD_OUT);
        }

        // 抢占号源：FOR UPDATE 行锁 + 当前读——并发事务各自锁住不同的最小可用号，
        // 3 号被前一个事务锁走后，后来的会取到 4 号，而不是全部撞在同一个号上
        NumberSource source = numberSourceMapper.selectOne(new LambdaQueryWrapper<NumberSource>()
                .eq(NumberSource::getScheduleId, dto.getScheduleId())
                .eq(NumberSource::getStatus, SourceStatus.AVAILABLE.getCode())
                .orderByAsc(NumberSource::getSourceNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (source == null) {
            log.warn("无可用号源, scheduleId={}, patientId={}", dto.getScheduleId(), dto.getPatientId());
            throw new BusinessException(ResultCode.SCHEDULE_SOLD_OUT);
        }
        int sourceRows = numberSourceMapper.update(null, new LambdaUpdateWrapper<NumberSource>()
                .eq(NumberSource::getId, source.getId())
                .eq(NumberSource::getStatus, SourceStatus.AVAILABLE.getCode())
                .set(NumberSource::getStatus, SourceStatus.LOCKED.getCode()));
        if (sourceRows == 0) {
            log.warn("号源抢占失败, sourceId={}, scheduleId={}", source.getId(), dto.getScheduleId());
            throw new BusinessException(ResultCode.SCHEDULE_SOLD_OUT);
        }

        // 落订单：号源此时是"锁定"（占着还没付钱），支付成功才变"已售出"，超时释放回"可用"
        Order order = new Order();
        order.setOrderNo(String.valueOf(redisIdWorker.nextId("order")));
        order.setUserId(patient.getUserId());
        order.setPatientId(dto.getPatientId());
        order.setPatientName(patient.getName());
        order.setScheduleId(dto.getScheduleId());
        order.setSourceId(source.getId());
        order.setAmount(schedule.getPrice());
        order.setStatus(OrderStatus.WAIT_PAY.getCode());
        order.setActiveFlag(1);
        order.setDeadlineTime(LocalDateTime.now().plusMinutes(OrderConstants.PAY_TIMEOUT_MINUTES));
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发双击/双请求同时越过预查，最终被 uk_patient_schedule 拦下：
            // 转成"已挂过该号"的精确提示，而不是笼统的 DATA_CONFLICT
            log.warn("并发重复挂号被唯一键拦截, patientId={}, scheduleId={}",
                    dto.getPatientId(), dto.getScheduleId());
            throw new BusinessException(ResultCode.ORDER_DUPLICATE);
        }

        return OrderVO.from(order, patient, schedule);
    }

    /**
     * 下单失败时回补 Redis 库存。
     * 用 Lua（EXISTS 才 INCR）而非裸 INCR：INCR 对不存在的 key 从 0 起算，会凭空造出假库存；
     * 回补自身的异常也不能把真正的下单失败原因顶掉，只记日志。
     */
    private void repayStock(String stockKey, boolean redisDeducted) {
        if (!redisDeducted) {
            return;
        }
        try {
            Long repaid = stringRedisTemplate.execute(stockRepayScript, List.of(stockKey));
            log.warn("下单失败已回补 Redis 库存, stockKey={}, result={}", stockKey, repaid);
        } catch (DataAccessException e) {
            log.error("Redis 库存回补失败, stockKey={}", stockKey, e);
        }
    }

    @Override
    public List<Long> listExpiredWaitPayOrderIds(int limit) {
        // 只查 id 列 + LIMIT 分批：超时单可能积压（服务重启/放量大），
        // 一次全捞会单轮跑很久，号一直被占着别人挂不上
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, OrderStatus.WAIT_PAY.getCode())
                        .lt(Order::getDeadlineTime, LocalDateTime.now())
                        .select(Order::getId)
                        .last("LIMIT " + limit))
                .stream()
                .map(Order::getId)
                .toList();
    }

    /**
     * 释放单个超时订单。给定时任务逐条调用，每条一个事务，一条失败不影响其他。
     *
     * 关键在"条件 UPDATE 抢取消权"：WHERE status=0 才改。定时任务扫出来到执行之间，
     * 用户可能恰好支付成功了（status 已变 1），这时 UPDATE 影响 0 行，本方法直接撒手，
     * 不会把"已支付的号"再释放掉。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseExpiredOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Integer.valueOf(OrderStatus.WAIT_PAY.getCode()).equals(order.getStatus())) {
            return;
        }

        // 抢取消权 + 唯一键让位：active_flag 置 NULL，让同一就诊人还能再挂同一排班
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.WAIT_PAY.getCode())
                .set(Order::getStatus, OrderStatus.CANCELED.getCode())
                .set(Order::getActiveFlag, null));
        if (rows == 0) {
            // 已被支付或已被其他线程释放，幂等跳过
            return;
        }

        // 号源回可用：锁定(1) → 可用(0)。必须查影响行数：0 行说明号源状态和预期不符
        // （脏数据/已被人工改动），这时库存已 +1 而号没回来，账就不平了——抛异常回滚整个事务，
        // 让定时任务下轮重试并靠日志报警，比静默吞掉好
        int sourceRows = numberSourceMapper.update(null, new LambdaUpdateWrapper<NumberSource>()
                .eq(NumberSource::getId, order.getSourceId())
                .eq(NumberSource::getStatus, SourceStatus.LOCKED.getCode())
                .set(NumberSource::getStatus, SourceStatus.AVAILABLE.getCode()));
        if (sourceRows == 0) {
            log.error("超时释放号源回补失败(状态与预期不符), orderId={}, sourceId={}",
                    orderId, order.getSourceId());
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        // 库存回补：数据库 +1
        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, order.getScheduleId())
                .setSql("left_num = left_num + 1"));

        // Redis 缓存作废：事务提交后再删（StockCacheUtil 内部注册 afterCommit），
        // 避免并发请求在提交前回填旧值导致少卖
        StockCacheUtil.evictAfterCommit(stringRedisTemplate, order.getScheduleId());

        log.info("超时订单已释放, orderId={}, scheduleId={}, sourceId={}",
                orderId, order.getScheduleId(), order.getSourceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_OWNED);
        }
        if (!Integer.valueOf(OrderStatus.PAID.getCode()).equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        Schedule schedule = scheduleMapper.selectById(order.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }
        // 签到窗口：只能就诊日当天签到，且时段还没结束（迟到但人到了，仍可签）
        if (!schedule.getWorkDate().isEqual(LocalDate.now())
                || SlotTimeUtil.isSlotEnded(schedule.getWorkDate(), schedule.getTimeSlot())) {
            throw new BusinessException(ResultCode.SIGN_NOT_ALLOWED);
        }

        // 防重：sign_time 为 NULL 才写入。第一次 NULL→有值 1 行；连点第二次有值→不匹配 0 行。
        // 判断与写入焊在一条 SQL，和"唯一键让位 / SETNX"同属一个思想家族。
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PAID.getCode())
                .isNull(Order::getSignTime)
                .set(Order::getSignTime, LocalDateTime.now()));
        if (rows == 0) {
            Order latest = orderMapper.selectById(orderId);
            if (latest != null && latest.getSignTime() != null) {
                throw new BusinessException(ResultCode.SIGN_DUPLICATED);
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
        log.info("签到成功, orderId={}, userId={}", orderId, userId);
    }

    @Override
    public List<Long> listToFinishOrderIds(int limit) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, OrderStatus.PAID.getCode(), OrderStatus.IN_PROGRESS.getCode())
                .select(Order::getId, Order::getScheduleId)
                .last("LIMIT " + limit));
        if (orders.isEmpty()) {
            return List.of();
        }
        // 批量查排班，只保留"时段已结束"（workDate <= 今天）的单。
        // 22:00 跑时今天所有时段都已结束，所以今天的单全部需要收尾，明天的单跳过
        Map<Long, Schedule> scheduleMap = scheduleMapper.selectBatchIds(
                        orders.stream().map(Order::getScheduleId).distinct().toList()).stream()
                .collect(Collectors.toMap(Schedule::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        return orders.stream()
                .filter(o -> {
                    Schedule s = scheduleMap.get(o.getScheduleId());
                    return s != null && !s.getWorkDate().isAfter(today);
                })
                .map(Order::getId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        Integer status = order.getStatus();
        boolean paid = Integer.valueOf(OrderStatus.PAID.getCode()).equals(status);
        boolean inProgress = Integer.valueOf(OrderStatus.IN_PROGRESS.getCode()).equals(status);
        if (!paid && !inProgress) {
            return;
        }
        // 已支付：已签到 → 已就诊，没签到 → 爽约
        // 就诊中：医生已叫号但未完成接诊，夜间强制收尾为已就诊
        int target;
        if (inProgress) {
            target = OrderStatus.FINISHED.getCode();
        } else {
            target = order.getSignTime() != null ? OrderStatus.FINISHED.getCode() : OrderStatus.NO_SHOW.getCode();
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, status)
                .set(Order::getStatus, target));
        if (rows > 0) {
            log.info("订单就诊状态收尾, orderId={}, from={}, target={}, signed={}",
                    orderId, status, target, order.getSignTime() != null);
        }
    }
}
