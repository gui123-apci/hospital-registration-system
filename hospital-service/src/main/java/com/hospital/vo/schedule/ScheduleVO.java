package com.hospital.vo.schedule;

import com.hospital.entity.Schedule;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 排班出参。
 *
 * id 是排班自己的主键——患者点【预约】时传给 /order 的 scheduleId 就是它；
 * doctorId、departmentId 是本行引用的外部主键，带前缀区分。
 *
 * 不放医生姓名、科室名称：患者是从医生列表点进来的，这些信息前端手里已有。
 */
@Data
public class ScheduleVO {

    private Long id;

    private Long doctorId;

    private Long departmentId;

    private LocalDate workDate;

    /** 1上午 2下午 3晚间 */
    private Integer timeSlot;

    private Integer totalNum;

    private Integer leftNum;

    private BigDecimal price;

    /** 1可预约 0停诊 */
    private Integer status;

    public static ScheduleVO from(Schedule schedule) {
        ScheduleVO vo = new ScheduleVO();
        vo.setId(schedule.getId());
        vo.setDoctorId(schedule.getDoctorId());
        vo.setDepartmentId(schedule.getDepartmentId());
        vo.setWorkDate(schedule.getWorkDate());
        vo.setTimeSlot(schedule.getTimeSlot());
        vo.setTotalNum(schedule.getTotalNum());
        vo.setLeftNum(schedule.getLeftNum());
        vo.setPrice(schedule.getPrice());
        vo.setStatus(schedule.getStatus());
        return vo;
    }
}
