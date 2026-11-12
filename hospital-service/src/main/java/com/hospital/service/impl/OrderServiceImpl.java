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
import com.hospital.vo.order.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // 3. 一人一单预查：给可读提示；真正兜底靠 uk_patient_schedule 唯一键
        Long exist = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getPatientId, dto.getPatientId())
                .eq(Order::getScheduleId, dto.getScheduleId()));
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

    @Override
    public PageResult<OrderVO> pageMine(int pageNum, int pageSize, Long userId) {
        // 分页参数钳制：与科室/医生/排班的 page() 保持一致
        long pn = Math.max(pageNum, 1);
        long ps = Math.min(Math.max(pageSize, 1), 100);
        Page<Order> page = orderMapper.selectPage(new Page<>(pn, ps), new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
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
        order.setScheduleId(dto.getScheduleId());
        order.setSourceId(source.getId());
        order.setAmount(schedule.getPrice());
        order.setStatus(OrderStatus.WAIT_PAY.getCode());
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
}
