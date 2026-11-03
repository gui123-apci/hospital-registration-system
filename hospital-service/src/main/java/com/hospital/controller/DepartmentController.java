package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.service.DepartmentService;
import com.hospital.vo.department.DepartmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 科室查询接口（患者端，无需登录）。
 */
@Tag(name = "科室查询", description = "患者端首页展示科室列表")
@RestController
@RequestMapping("/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "查询全部科室")
    @GetMapping("/list")
    public Result<List<DepartmentVO>> list() {
        return Result.success(departmentService.list());
    }
}