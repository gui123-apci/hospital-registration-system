package com.hospital.controller;

import com.hospital.common.result.PageResult;
import com.hospital.common.result.Result;
import com.hospital.dto.doctor.DoctorCreateDTO;
import com.hospital.dto.doctor.DoctorUpdateDTO;
import com.hospital.service.DoctorService;
import com.hospital.vo.doctor.DoctorVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 医生管理接口（仅管理员可访问 /admin/**）。
 */
@Tag(name = "医生管理", description = "管理员增删改查医生")
@RestController
@RequestMapping("/admin/doctor")
@RequiredArgsConstructor
public class AdminDoctorController {

    private final DoctorService doctorService;

    @Operation(summary = "新增医生")
    @PostMapping
    public Result<DoctorVO> create(@Valid @RequestBody DoctorCreateDTO dto) {
        return Result.success(doctorService.create(dto));
    }

    @Operation(summary = "更新医生")
    @PutMapping
    public Result<DoctorVO> update(@Valid @RequestBody DoctorUpdateDTO dto) {
        return Result.success(doctorService.update(dto));
    }

    @Operation(summary = "删除医生")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        doctorService.delete(id);
        return Result.success();
    }

    @Operation(summary = "根据 ID 查询医生")
    @GetMapping("/{id}")
    public Result<DoctorVO> getById(@PathVariable Long id) {
        return Result.success(doctorService.getById(id));
    }

    @Operation(summary = "查询全部医生（管理端下拉框用）")
    @GetMapping("/list")
    public Result<List<DoctorVO>> list() {
        return Result.success(doctorService.list());
    }

    @Operation(summary = "分页查询医生列表")
    @GetMapping("/page")
    public Result<PageResult<DoctorVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(doctorService.page(pageNum, pageSize));
    }
}
