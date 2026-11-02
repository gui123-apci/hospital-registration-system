package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 排班，对应 tb_schedule。
 * leftNum 是秒杀时的库存字段，M2 会重点使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_schedule")
public class Schedule extends BaseEntity {

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
}
