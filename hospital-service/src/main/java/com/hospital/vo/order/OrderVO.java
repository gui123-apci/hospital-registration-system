package com.hospital.vo.order;

import com.hospital.entity.Order;
import com.hospital.entity.Patient;
import com.hospital.entity.Schedule;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 挂号单出参。
 *
 * 不放医生名/科室名：挂号是患者从医生详情页点进来的，这些信息前端手里已有；
 * 放 patientName 是让前端知道这张单是给谁挂的（一个账号可挂多个家人）。
 */
@Data
public class OrderVO {

    private Long id;

    /** 订单号，展示给用户、支付时回传 */
    private String orderNo;

    private Long patientId;

    private String patientName;

    private Long scheduleId;

    private LocalDate workDate;

    /** 1上午 2下午 3晚间 */
    private Integer timeSlot;

    private BigDecimal amount;

    /** 0待支付 1已支付 2已退号 3已就诊 4已取消 5爽约 */
    private Integer status;

    private LocalDateTime deadlineTime;

    private LocalDateTime createTime;

    public static OrderVO from(Order order, Patient patient, Schedule schedule) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPatientId(order.getPatientId());
        vo.setPatientName(patient != null ? patient.getName() : null);
        vo.setScheduleId(order.getScheduleId());
        vo.setWorkDate(schedule != null ? schedule.getWorkDate() : null);
        vo.setTimeSlot(schedule != null ? schedule.getTimeSlot() : null);
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setDeadlineTime(order.getDeadlineTime());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}
