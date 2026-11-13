package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 挂号单，对应 tb_order。
 * status：0待支付 1已支付 2已退号 3已就诊 4已取消 5爽约。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_order")
public class Order extends BaseEntity {

    private String orderNo;

    private Long userId;

    private Long patientId;

    private Long scheduleId;

    private Long sourceId;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime payTime;

    /** 支付截止时间，超时后释放号源 */
    private LocalDateTime deadlineTime;

    /** 到院签到时间 */
    private LocalDateTime signTime;

    /**
     * 有效标记：下单填 1，退号/超时释放置 NULL。
     * 配合唯一键 uk_patient_schedule(patient_id, schedule_id, active_flag) 实现"一人一单让位"：
     * MySQL 唯一索引不比较 NULL，置 NULL 后该行不再占用唯一键位，同一就诊人可再次挂同一排班。
     */
    private Integer activeFlag;
}
