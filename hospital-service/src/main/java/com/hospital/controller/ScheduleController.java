package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.service.ScheduleService;
import com.hospital.vo.schedule.ScheduleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者端排班查询。
 * 患者从医生列表点进某位医生，查看其未来几天的可约排班。
 */
@Tag(name = "患者端排班", description = "患者查看医生排班")
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * @param doctorId 医生 ID（必传）
     * @param days     查未来几天，默认 7 天
     */
    @Operation(summary = "查询某医生未来可预约的排班")
    @GetMapping("/list")
    public Result<List<ScheduleVO>> list(@RequestParam Long doctorId,
                                         @RequestParam(defaultValue = "7") int days) {
        return Result.success(scheduleService.listForPatient(doctorId, days));
    }
}
