package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.hospital.vo.schedule.ScheduleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 排班业务实现。
 */
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final NumberSourceMapper numberSourceMapper;
    private final OrderMapper orderMapper;

    /**
     * 各时段开始时刻，下标即 timeSlot：1上午 8:00、2下午 13:00、3晚间 18:00（下标 0 占位）。
     */
    private static final LocalTime[] SLOT_START = {
            LocalTime.MIDNIGHT,
            LocalTime.of(8, 0),
            LocalTime.of(13, 0),
            LocalTime.of(18, 0)
    };

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleCreateDTO dto) {
        Doctor doctor = doctorMapper.selectById(dto.getDoctorId());
        if (doctor == null) {
            throw new BusinessException(ResultCode.DOCTOR_NOT_FOUND);
        }

        // 日期级注解只能拦"昨天"，拦不住"今天上午的号下午才放"，所以时刻判断放这里
        if (isSlotExpired(dto.getWorkDate(), dto.getTimeSlot())) {
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
        schedule.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        scheduleMapper.insert(schedule);

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
            if (isSlotExpired(newDate, newSlot)) {
                throw new BusinessException(ResultCode.SCHEDULE_SLOT_EXPIRED);
            }
            if (existsConflict(exist.getDoctorId(), newDate, newSlot, exist.getId())) {
                throw new BusinessException(ResultCode.SCHEDULE_TIME_CONFLICT);
            }
        }

        // 已过期的排班不能被改回"可预约"：日期时段没变时上面的校验被跳过，这里补上，
        // 否则管理端会出现"今天上午已过却显示可预约"的记录
        if (Integer.valueOf(1).equals(dto.getStatus()) && isSlotExpired(newDate, newSlot)) {
            throw new BusinessException(ResultCode.SCHEDULE_SLOT_EXPIRED);
        }

        // 调整放号总数：已挂出 = 原总数 - 原剩余，新剩余 = 新总数 - 已挂出
        if (dto.getTotalNum() != null && !dto.getTotalNum().equals(exist.getTotalNum())) {
            int sold = exist.getTotalNum() - exist.getLeftNum();
            int newLeft = dto.getTotalNum() - sold;
            if (newLeft < 0) {
                throw new BusinessException(ResultCode.SCHEDULE_TOTAL_TOO_SMALL);
            }
            exist.setTotalNum(dto.getTotalNum());
            exist.setLeftNum(newLeft);
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

        return ScheduleVO.from(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (scheduleMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.SCHEDULE_NOT_FOUND);
        }

        // 差异点：两条引用路径——号源明细、挂号单
        Long sourceCount = numberSourceMapper.selectCount(
                new LambdaQueryWrapper<NumberSource>().eq(NumberSource::getScheduleId, id));
        Long orderCount = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getScheduleId, id));
        if ((sourceCount != null && sourceCount > 0) || (orderCount != null && orderCount > 0)) {
            throw new BusinessException(ResultCode.SCHEDULE_IN_USE);
        }

        scheduleMapper.deleteById(id);
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
                .eq(Schedule::getStatus, 1)
                // 过期不展示：只给今天及以后
                .ge(Schedule::getWorkDate, today)
                .le(Schedule::getWorkDate, today.plusDays(effectiveDays - 1L))
                .orderByAsc(Schedule::getWorkDate)
                .orderByAsc(Schedule::getTimeSlot);
        // 今天已过的时段不再展示，和"过点不许放号"保持同一套判断
        return scheduleMapper.selectList(wrapper).stream()
                .filter(schedule -> !isSlotExpired(schedule.getWorkDate(), schedule.getTimeSlot()))
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
     * 该排班时段是否已经过了：日期已过，或者就是今天但时段开始时刻已到。
     * workDate 精确到天，所以"今天上午"只能在运行时用时刻判断。
     */
    private boolean isSlotExpired(LocalDate workDate, Integer timeSlot) {
        LocalDate today = LocalDate.now();
        if (workDate.isBefore(today)) {
            return true;
        }
        // 脏数据防御：数据库 time_slot 没有 CHECK 约束，越界值按"已过期"处理，
        // 避免数组越界抛系统异常，也避免 time_slot=0 被当成永不过期
        if (timeSlot == null || timeSlot < 1 || timeSlot > 3) {
            return true;
        }
        return workDate.isEqual(today) && !LocalTime.now().isBefore(SLOT_START[timeSlot]);
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
}
