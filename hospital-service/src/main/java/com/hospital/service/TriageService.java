package com.hospital.service;

import com.hospital.dto.triage.TriageAskDTO;
import com.hospital.vo.triage.TriageVO;
import reactor.core.publisher.Flux;

/**
 * AI 导诊业务接口。
 */
public interface TriageService {

    /**
     * 根据症状描述推荐科室。
     * userId 用于限流（同一用户 5 秒内只放一个）。
     */
    TriageVO ask(TriageAskDTO dto, Long userId);

    /**
     * 流式导诊：返回 Flux<String>，每个元素是模型输出的一小段文本。
     * 工具执行结果（结构化号源）在流结束后通过 TriageScheduleHolder 获取。
     */
    Flux<String> askStream(TriageAskDTO dto, Long userId);
}
