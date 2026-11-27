package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.service.DashboardService;
import com.hospital.vo.dashboard.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端数据看板：订单统计、挂号趋势、科室分布。
 */
@Tag(name = "数据看板", description = "管理端订单统计与图表数据")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "看板汇总数据", description = "今日挂号量、收入、订单状态分布、科室占比、7天趋势")
    @GetMapping
    public Result<DashboardVO> overview() {
        return Result.success(dashboardService.overview());
    }
}
