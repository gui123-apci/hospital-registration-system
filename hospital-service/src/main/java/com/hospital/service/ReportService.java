package com.hospital.service;

import com.hospital.vo.report.ReportVO;

import java.util.List;

/**
 * 检验报告业务接口（患者侧查询）。
 */
public interface ReportService {

    /**
     * 查当前账号名下所有就诊人的报告。归属校验从"我的就诊人"向下查，天然不越权。
     */
    List<ReportVO> listMine(Long userId);
}
