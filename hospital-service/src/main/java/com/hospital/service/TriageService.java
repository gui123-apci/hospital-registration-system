package com.hospital.service;

import com.hospital.dto.triage.TriageAskDTO;
import com.hospital.vo.triage.TriageVO;

/**
 * AI 导诊业务接口。
 */
public interface TriageService {

    /**
     * 根据症状描述推荐科室。
     * userId 用于限流（同一用户 5 秒内只放一个）。
     */
    TriageVO ask(TriageAskDTO dto, Long userId);
}
