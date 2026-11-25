package com.hospital.vo.triage;

import lombok.Data;

/**
 * AI 导诊出参：Agent 的自然语言回答。
 * 不再返回结构化科室字段——Agent 的回答本身已经包含科室/医生/号源信息。
 */
@Data
public class TriageVO {

    /** Agent 给患者的完整回答，如"建议挂心内科，李医生周三下午还有 5 个号" */
    private String answer;

    public static TriageVO of(String answer) {
        TriageVO vo = new TriageVO();
        vo.setAnswer(answer);
        return vo;
    }
}
