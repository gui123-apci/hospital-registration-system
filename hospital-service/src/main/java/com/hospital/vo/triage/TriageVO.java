package com.hospital.vo.triage;

import lombok.Data;

import java.util.List;

/**
 * AI 导诊出参：Agent 的自然语言回答 + 可预约号源。
 *
 * 两者来源不同：answer 是模型自由组织的一句话，schedules 是工具查到的结构化号源
 * （含三个 id）。前端拿 answer 展示建议、拿 schedules 渲染"一键挂号"入口。
 */
@Data
public class TriageVO {

    /** Agent 给患者的完整回答，如"建议挂心内科，李医生周三下午还有 5 个号" */
    private String answer;

    /** 可预约号源（可能为空：症状无需挂号、模型没查号源、或降级） */
    private List<TriageScheduleVO> schedules;

    public static TriageVO of(String answer, List<TriageScheduleVO> schedules) {
        TriageVO vo = new TriageVO();
        vo.setAnswer(answer);
        vo.setSchedules(schedules);
        return vo;
    }
}
