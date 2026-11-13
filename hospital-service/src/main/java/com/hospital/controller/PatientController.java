package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.dto.patient.PatientCreateDTO;
import com.hospital.dto.patient.PatientUpdateDTO;
import com.hospital.security.LoginUser;
import com.hospital.service.PatientService;
import com.hospital.vo.patient.PatientVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者端就诊人接口（需登录）。
 * userId 从 token 解出，不由前端传。
 */
@Tag(name = "就诊人", description = "就诊人管理：新增、列表、补全信息")
@RestController
@RequestMapping("/patient")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @Operation(summary = "我的就诊人列表")
    @GetMapping("/list")
    public Result<List<PatientVO>> listMine(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(patientService.listMine(loginUser.getUserId()));
    }

    @Operation(summary = "新增就诊人")
    @PostMapping
    public Result<PatientVO> create(@Valid @RequestBody PatientCreateDTO dto,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success("就诊人添加成功", patientService.create(dto, loginUser.getUserId()));
    }

    @Operation(summary = "更新就诊人")
    @PutMapping
    public Result<PatientVO> update(@Valid @RequestBody PatientUpdateDTO dto,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(patientService.update(dto, loginUser.getUserId()));
    }
}
