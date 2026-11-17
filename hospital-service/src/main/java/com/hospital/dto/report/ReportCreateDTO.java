package com.hospital.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 医生开具检验报告入参。
 * 报告只长在"已就诊"的订单上，orderId 是唯一定位依据。
 */
@Data
public class ReportCreateDTO {

    @NotNull(message = "订单 id 不能为空")
    private Long orderId;

    @Size(max = 32, message = "报告类型不能超过 32 个字符")
    private String reportType;

    /** 标题必填：没有标题的报告在患者列表里是一行空白，无法辨认 */
    @NotBlank(message = "报告标题不能为空")
    @Size(max = 64, message = "报告标题不能超过 64 个字符")
    private String title;

    /** 对应 TEXT 列，不设长度上限 */
    private String content;
}
