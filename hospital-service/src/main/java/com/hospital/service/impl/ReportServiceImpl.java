package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.entity.Patient;
import com.hospital.entity.Report;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.ReportMapper;
import com.hospital.service.ReportService;
import com.hospital.vo.report.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检验报告查询实现。
 *
 * tb_report 没有 user_id 字段，归属校验不靠"查出报告再比对"，
 * 而是从归属出发向下查：先拿"我的就诊人 id 列表"，再查 patient_id IN 列表。
 * 查询的起点就是归属本身，天然不可能越权。
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final PatientMapper patientMapper;

    @Override
    public List<ReportVO> listMine(Long userId) {
        List<Long> patientIds = patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId)
                        .select(Patient::getId))
                .stream()
                .map(Patient::getId)
                .toList();
        if (patientIds.isEmpty()) {
            return List.of();
        }

        List<Report> reports = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .in(Report::getPatientId, patientIds)
                .orderByDesc(Report::getId));
        if (reports.isEmpty()) {
            return List.of();
        }

        Map<Long, Patient> patientMap = patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        return reports.stream()
                .map(r -> {
                    Patient p = patientMap.get(r.getPatientId());
                    return ReportVO.from(r, p != null ? p.getName() : null);
                })
                .toList();
    }
}
