package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.service.DoctorService;
import com.hospital.vo.doctor.DoctorVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 医生查询接口（患者端，需登录但无需管理员）。
 */
@Tag(name = "医生查询", description = "患者端按科室查医生")
@RestController
@RequestMapping("/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @Operation(summary = "查询医生列表（可按科室过滤）")
    @GetMapping("/list")
    public Result<List<DoctorVO>> list(@RequestParam(required = false) Long departmentId) {
        return Result.success(doctorService.list(departmentId));
    }
}
