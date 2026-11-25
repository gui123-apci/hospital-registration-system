package com.hospital.dto.triage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 导诊入参：只收症状描述。
 * userId 从 token 解出（限流按它做），不由前端传。
 */
@Data
public class TriageAskDTO {

    @NotBlank(message = "请描述您的症状")
    @Size(max = 500, message = "症状描述过长（最多 500 字）")
    private String symptoms;
}
