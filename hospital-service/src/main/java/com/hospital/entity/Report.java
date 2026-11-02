package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 检验报告，对应 tb_report。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_report")
public class Report extends BaseEntity {

    private Long patientId;

    private Long orderId;

    private Long doctorId;

    private String reportType;

    private String title;

    private String content;

    private LocalDateTime reportTime;
}
