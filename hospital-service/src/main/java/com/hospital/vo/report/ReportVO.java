package com.hospital.vo.report;

import com.hospital.entity.Report;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检验报告出参。
 */
@Data
public class ReportVO {

    private Long id;

    private Long patientId;

    private String patientName;

    private Long orderId;

    private String reportType;

    private String title;

    private String content;

    private LocalDateTime reportTime;

    public static ReportVO from(Report report, String patientName) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setPatientId(report.getPatientId());
        vo.setPatientName(patientName);
        vo.setOrderId(report.getOrderId());
        vo.setReportType(report.getReportType());
        vo.setTitle(report.getTitle());
        vo.setContent(report.getContent());
        vo.setReportTime(report.getReportTime());
        return vo;
    }
}
