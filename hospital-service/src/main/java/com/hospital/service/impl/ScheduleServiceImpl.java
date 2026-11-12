package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.constant.RedisKeyConstants;
import com.hospital.common.enums.ScheduleStatus;
import com.hospital.common.enums.SourceStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.PageResult;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.schedule.ScheduleCreateDTO;
import com.hospital.dto.schedule.ScheduleUpdateDTO;
import com.hospital.entity.Doctor;
import com.hospital.entity.NumberSource;
import com.hospital.entity.Order;
import com.hospital.entity.Schedule;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.NumberSourceMapper;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.service.ScheduleService;
import com.hospital.util.SlotTimeUtil;
import com.hospital.vo.schedule.ScheduleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final NumberSourceMapper numberSourceMapper;
    private final OrderMapper orderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleCreateDTO dto) {
        Doctor doctor = doctorMapper.selectById(dto.getDoctorId());
        if (doctor == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_FOUND);
        }

        // 日期级注解只能拦"昨天"，拦不住"今天上午的号下午才放"，所以时刻判断放这里
        if (SlotTimeUtil.isExpired(dto.getWorkDate(), dto.getTimeSlot())) {
            throw new BusinessException(ResultCode.SCHEDULE_SLOT_EXPIRED);
        }

        // 预查重复：只为给出可读的业务提示；真正防住并发的是数据库唯一键 uk_doctor_date_slot
        if (existsConflict(dto.getDoctorId(), dto.getWorkDate(), dto.getTimeSlot(), null)) {
            throw new BusinessException(ResultCode.SCHEDULE_TIME_CONFLICT);
        }

        Schedule schedule = new Schedule();
        schedule.setDoctorId(dto.getDoctorId());
        // 科室跟着医生走，不让前端传，避免两边对不上
        schedule.setDepartmentId(doctor.getDepartmentId());
        schedule.setWorkDate(dto.getWorkDate());
        schedule.setTimeSlot(dto.getTimeSlot());
        schedule.setTotalNum(dto.getTotalNum());
        // 剩余号数初始等于放号总数，之后只能由挂号扣减
        schedule.setLeftNum(dto.getTotalNum());
        schedule.setPrice(dto.getPrice());
        schedule.setStatus(dto.getStatus() == null ? ScheduleStatus.AVAILABLE.getCode() : dto.getStatus());
        scheduleMapper.insert(schedule);

        // 放号时批量生成号源：一条排班 N 个号，号源跟着出生，号序 1~N 就是叫号顺序
        for (int i = 1; i <= schedule.getTotalNum(); i++) {
            NumberSource source = new NumberSource();
            source.setScheduleId(schedule.getId());
            source.setSourceNo(i);
            source.setStatus(SourceStatus.AVAILABLE.getCode());
            numberSourceMapper.insert(source);
        }

        return ScheduleVO.from(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO update(ScheduleUpdateDTO dto) {
        Schedule exist = scheduleMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }

        LocalDate newDate = dto.getWorkDate() == null ? exist.getWorkDate() : dto.getWorkDate();
        Integer newSlot = dto.getTimeSlot() == null ? exist.getTimeSlot() : dto.getTimeSlot();
        // 日期或时段变了，才需要重新校验（排除自己）
        if (!newDate.equals(exist.getWorkDate()) || !newSlot.equals(exist.getTimeSlot())) {
            if (SlotTimeUtil.isExpired(newDate, newSlot)) {
                throw new BusinessException(ResultCode.SCHEDULE_SLOT_EXPIRED);
            }
            if (existsConflict(exist.getDoctorId(), newDate, newSlot, exist.getId())) {
                throw new BusinessException(ResultCode.SCHEDULE_TIME_CONFLICT);
            }
        }

        // 已过期的排班不能被改回"可预约"：日期时段没变时上面的校验被跳过，这里补上，
        // 否则管理端会出现"今天上午已过却显示可预约"的记录
        if (Integer.valueOf(ScheduleStatus.AVAILABLE.getCode()).equals(dto.getStatus()) && SlotTimeUtil.isExpired(newDate, newSlot)) {
            throw new BusinessException(ResultCode.SCHEDULE_SLOT_EXPIRED);
        }

        // 调整放号总数：已挂出 = 原总数 - 原剩余，新剩余 = 新总数 - 已挂出
        boolean totalChanged = false;
        if (dto.getTotalNum() != null && !dto.getTotalNum().equals(exist.getTotalNum())) {
            int sold = exist.getTotalNum() - exist.getLeftNum();
            int newLeft = dto.getTotalNum() - sold;
            if (newLeft < 0) {
                throw new BusinessException(ResultCode.SCHEDULE_TOTAL_TOO_SMALL);
            }
            exist.setTotalNum(dto.getTotalNum());
            exist.setLeftNum(newLeft);
            totalChanged = true;
        }

        exist.setWorkDate(newDate);
        exist.setTimeSlot(newSlot);
        if (dto.getPrice() != null) {
            exist.setPrice(dto.getPrice());
        }
        if (dto.getStatus() != null) {
            exist.setStatus(dto.getStatus());
        }
        scheduleMapper.updateById(exist);

        // 放号总数变了，Redis 里的库存缓存必须失效，否则患者端约到的还是旧库存。
        // Cache-Aside 标准动作：改库之后删缓存，下次读自动回填新值。
        // Redis 删除失败不回滚数据库：删不掉只是缓存慢一步过期，账不会错
        if (totalChanged) {
            evictStockCache(exist.getId());
        }

        return ScheduleVO.from(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (scheduleMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }

        // 有挂号单就不能删；号源是排班的附属物（放号时生成），删排班时一起级联删除
        Long orderCount = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getScheduleId, id));
        if (orderCount != null && orderCount > 0) {
            throw new BusinessException(ResultCode.SCHEDULE_IN_USE);
        }

        numberSourceMapper.delete(new LambdaQueryWrapper<NumberSource>()
                .eq(NumberSource::getScheduleId, id));
        scheduleMapper.deleteById(id);

        // 删排班时清掉 Redis 库存缓存，防止 key 残留成垃圾
        evictStockCache(id);
    }

    @Override
    public ScheduleVO getById(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }
        return ScheduleVO.from(schedule);
    }

    @Override
    public List<ScheduleVO> listForPatient(Long doctorId, int days) {
        // 医生不存在直接报错，让前端能区分"医生不存在"和"暂时没有排班"
        if (doctorMapper.selectById(doctorId) == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_FOUND);
        }
        // days 钳制在 1~31：传大数拉海量数据、传负数返回空列表，都不是正常业务
        int effectiveDays = Math.min(Math.max(days, 1), 31);
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctorId)
                // 停诊不展示：展示出来患者点了也挂不上
                .eq(Schedule::getStatus, ScheduleStatus.AVAILABLE.getCode())
                // 过期不展示：只给今天及以后
                .ge(Schedule::getWorkDate, today)
                .le(Schedule::getWorkDate, today.plusDays(effectiveDays - 1L))
                .orderByAsc(Schedule::getWorkDate)
                .orderByAsc(Schedule::getTimeSlot);
        // 今天已过的时段不再展示，和"过点不许放号"保持同一套判断
        return scheduleMapper.selectList(wrapper).stream()
                .filter(schedule -> !SlotTimeUtil.isExpired(schedule.getWorkDate(), schedule.getTimeSlot()))
                .map(ScheduleVO::from)
                .toList();
    }

    @Override
    public PageResult<ScheduleVO> page(long pageNum, long pageSize, Long doctorId) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        if (doctorId != null) {
            wrapper.eq(Schedule::getDoctorId, doctorId);
        }
        wrapper.orderByAsc(Schedule::getWorkDate).orderByAsc(Schedule::getTimeSlot);
        // 分页参数钳制：页码至少 1，每页最多 100，防止一次拉全表
        long pn = Math.max(pageNum, 1);
        long ps = Math.min(Math.max(pageSize, 1), 100);
        Page<Schedule> page = scheduleMapper.selectPage(new Page<>(pn, ps), wrapper);
        return PageResult.of(page, ScheduleVO::from);
    }

    /**
     * 同一医生、同一天、同一时段是否已有排班。
     *
     * @param excludeId 更新时传自己的 id，把自身排除掉
     */
    private boolean existsConflict(Long doctorId, LocalDate workDate, Integer timeSlot, Long excludeId) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctorId)
                .eq(Schedule::getWorkDate, workDate)
                .eq(Schedule::getTimeSlot, timeSlot);
        if (excludeId != null) {
            wrapper.ne(Schedule::getId, excludeId);
        }
        Long count = scheduleMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 删除 Redis 里的排班库存缓存。失败只记日志不抛异常：
     * 缓存删不掉的后果是短暂读到旧库存，数据库兜底不会让账出错。
     */
    private void evictStockCache(Long scheduleId) {
        try {
            stringRedisTemplate.delete(RedisKeyConstants.STOCK_PREFIX + scheduleId);
        } catch (Exception e) {
            log.warn("删除 Redis 库存缓存失败, scheduleId={}", scheduleId, e);
        }
    }
}
