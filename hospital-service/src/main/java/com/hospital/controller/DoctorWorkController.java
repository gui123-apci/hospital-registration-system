package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.dto.report.ReportCreateDTO;
import com.hospital.security.LoginUser;
import com.hospital.service.DoctorWorkService;
import com.hospital.vo.doctor.WaitingVO;
import com.hospital.vo.order.OrderVO;
import com.hospital.vo.report.ReportVO;
import com.hospital.vo.schedule.ScheduleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 医生工作台接口（医生/管理员角色，SecurityConfig 按路径拦）。
 * doctorUserId 从 token 解出，服务端据此反查医生并校验排班归属。
 */
@Tag(name = "医生工作台", description = "叫号、开具检验报告")
@RestController
@RequestMapping("/doctor-work")
@RequiredArgsConstructor
public class DoctorWorkController {

    private final DoctorWorkService doctorWorkService;

    @Operation(summary = "我的出诊排班", description = "当前登录医生今天尚未结束的时段及未来排班，按日期、时段升序")
    @GetMapping("/schedules")
    public Result<List<ScheduleVO>> listMySchedules(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(doctorWorkService.listMySchedules(loginUser.getUserId()));
    }

    @Operation(summary = "候诊队列", description = "该排班下已签到未就诊的全部患者，按签到时间排序（候诊大屏/医生端列表）")
    @GetMapping("/waiting")
    public Result<List<WaitingVO>> listWaiting(@RequestParam Long scheduleId,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(doctorWorkService.listWaiting(scheduleId, loginUser.getUserId()));
    }

    @Operation(summary = "叫号", description = "按签到顺序叫下一位已签到患者，订单进入就诊中")
    @PostMapping("/call")
    public Result<OrderVO> callNext(@RequestParam Long scheduleId,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(doctorWorkService.callNext(scheduleId, loginUser.getUserId()));
    }

    @Operation(summary = "完成接诊", description = "把就诊中订单置为已就诊，标志本次问诊结束")
    @PostMapping("/complete")
    public Result<OrderVO> completeConsultation(@RequestParam Long orderId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(doctorWorkService.completeConsultation(orderId, loginUser.getUserId()));
    }

    @Operation(summary = "开具检验报告", description = "仅限已就诊订单，且订单归属当前医生")
    @PostMapping("/report")
    public Result<ReportVO> createReport(@Valid @RequestBody ReportCreateDTO dto,
                                         @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(doctorWorkService.createReport(dto, loginUser.getUserId()));
    }
}
