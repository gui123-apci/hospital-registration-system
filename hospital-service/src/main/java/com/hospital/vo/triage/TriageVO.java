package com.hospital.vo.triage;

import lombok.Data;

/**
 * AI 导诊出参：推荐科室 + 理由 + 是否建议急诊 + 结果来源。
 * source 用于前端演示与排查（AI / CACHE / RULE）。
 */
@Data
public class TriageVO {

    private Long departmentId;

    private String departmentName;

    /** 推荐理由（AI 生成或规则文案） */
    private String reason;

    /** 是否建议急诊 */
    private Boolean urgent;

    /** 结果来源：AI / CACHE / RULE */
    private String source;
}
