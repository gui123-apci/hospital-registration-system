package com.hospital.controller;

import com.hospital.common.result.PageResult;
import com.hospital.common.result.Result;
import com.hospital.dto.department.DepartmentCreateDTO;
import com.hospital.dto.department.DepartmentUpdateDTO;
import com.hospital.service.DepartmentService;
import com.hospital.vo.department.DepartmentVO;
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
 * 科室管理接口（仅管理员可访问 /admin/**）。
 */
@Tag(name = "科室管理", description = "管理员增删改查科室")
@RestController
@RequestMapping("/admin/department")
@RequiredArgsConstructor
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "新增科室")
    @PostMapping
    public Result<DepartmentVO> create(@Valid @RequestBody DepartmentCreateDTO dto) {
        return Result.success(departmentService.create(dto));
    }

    @Operation(summary = "更新科室")
    @PutMapping
    public Result<DepartmentVO> update(@Valid @RequestBody DepartmentUpdateDTO dto) {
        return Result.success(departmentService.update(dto));
    }

    @Operation(summary = "删除科室")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "根据 ID 查询科室")
    @GetMapping("/{id}")
    public Result<DepartmentVO> getById(@PathVariable Long id) {
        return Result.success(departmentService.getById(id));
    }

    @Operation(summary = "分页查询科室列表")
    @GetMapping("/page")
    public Result<PageResult<DepartmentVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(departmentService.page(pageNum, pageSize));
    }

    // AdminDepartmentController 里再加一个
    @Operation(summary = "查询全部科室（管理端下拉框用）")
    @GetMapping("/list")
    public Result<List<DepartmentVO>> list() {
        return Result.success(departmentService.list());
    }
}