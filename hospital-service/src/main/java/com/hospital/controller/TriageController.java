package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.dto.triage.TriageAskDTO;
import com.hospital.security.LoginUser;
import com.hospital.service.TriageService;
import com.hospital.vo.triage.TriageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 导诊接口（患者端，需登录）。
 * userId 从 token 解出，供限流使用。
 */
@Tag(name = "AI 导诊", description = "患者描述症状，返回推荐科室")
@RestController
@RequestMapping("/triage")
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;

    @Operation(summary = "智能导诊", description = "根据症状描述推荐科室，AI 不可用时自动降级关键词匹配")
    @PostMapping("/ask")
    public Result<TriageVO> ask(@Valid @RequestBody TriageAskDTO dto,
                                @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(triageService.ask(dto, loginUser.getUserId()));
    }
}
