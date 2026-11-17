package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.security.LoginUser;
import com.hospital.service.ReportService;
import com.hospital.vo.report.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者端检验报告查询接口（需登录）。
 * userId 从 token 解出，返回当前账号名下所有就诊人的报告。
 */
@Tag(name = "检验报告", description = "患者查看自己的报告")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "我的报告列表")
    @GetMapping("/list")
    public Result<List<ReportVO>> listMine(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(reportService.listMine(loginUser.getUserId()));
    }
}
