package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 检验报告，对应 tb_report。
 * M3 报告模块才开发完整功能，现在只为医生删除时的引用检查提供实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_report")
public class Report extends BaseEntity {

    private Long patientId;

    private Long orderId;

    /** 出报告医生，可空 */
    private Long doctorId;

    private String reportType;

    private String title;

    private String content;

    private LocalDateTime reportTime;
}
