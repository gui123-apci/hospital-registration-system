package com.hospital.controller;

import com.hospital.common.result.PageResult;
import com.hospital.common.result.Result;
import com.hospital.dto.schedule.ScheduleCreateDTO;
import com.hospital.dto.schedule.ScheduleUpdateDTO;
import com.hospital.service.ScheduleService;
import com.hospital.vo.schedule.ScheduleVO;
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

/**
 * 排班管理接口（仅管理员可访问 /admin/**）。
 */
@Tag(name = "排班管理", description = "管理员放号、改号、停诊、删除排班")
@RestController
@RequestMapping("/admin/schedule")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "新增排班（放号）")
    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleCreateDTO dto) {
        return Result.success(scheduleService.create(dto));
    }

    @Operation(summary = "更新排班")
    @PutMapping
    public Result<ScheduleVO> update(@Valid @RequestBody ScheduleUpdateDTO dto) {
        return Result.success(scheduleService.update(dto));
    }

    @Operation(summary = "删除排班")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "根据 ID 查询排班")
    @GetMapping("/{id}")
    public Result<ScheduleVO> getById(@PathVariable Long id) {
        return Result.success(scheduleService.getById(id));
    }

    @Operation(summary = "分页查询排班（可按医生过滤）")
    @GetMapping("/page")
    public Result<PageResult<ScheduleVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long doctorId) {
        return Result.success(scheduleService.page(pageNum, pageSize, doctorId));
    }
}
