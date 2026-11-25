package com.hospital.service.impl;

import com.hospital.client.AiClient;
import com.hospital.common.constant.RedisKeyConstants;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.triage.TriageAskDTO;
import com.hospital.service.TriageService;
import com.hospital.vo.triage.TriageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 导诊实现（Agent 版）。
 *
 * 流程只有两步：限流（防连点烧钱）→ 调 Agent（模型自己决定查科室/医生/号源）。
 * 模型挂掉时优雅降级——给一句友好提示，而不是报 500。
 *
 * 和单轮问答版的核心区别：不再有"回库校验 + 关键词兜底"。
 * 单轮版要靠回库校验防模型编科室名，靠关键词兜底保底；
 * Agent 版把"查科室"本身做成工具，模型查出来的就是库里真实数据，天然不会编。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageServiceImpl implements TriageService {

    /** 告诉模型它是谁、能调哪些工具、最终怎么回答 */
    private static final String SYSTEM_PROMPT = "你是医院导诊助手。患者会描述自己的症状，" +
            "你可以调用工具查询科室、医生和号源信息。请根据症状判断合适的科室，" +
            "必要时查询该科室的医生和最近可预约的号源，最后用一句自然语言告诉患者：" +
            "建议挂哪个科室、有哪些医生可选、最近什么时候有号。如果症状危险（呼吸困难、剧烈胸痛等），提醒患者尽快就诊。";

    /** 模型挂掉时的优雅降级文案：承认服务不可用，引导患者走其他途径，而不是报错 */
    private static final String DEGRADE_ANSWER = "AI 导诊暂时不可用，请到科室列表自助选择或咨询导诊台。";

    private final AiClient aiClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public TriageVO ask(TriageAskDTO dto, Long userId) {
        String symptoms = dto.getSymptoms().trim();

        // 限流：同一用户 5 秒内只放一个。Agent 一次回答要调好几次模型，比单轮更烧钱，
        // 连点/刷新会成倍消耗，所以限流比原来更重要
        if (isRateLimited(userId)) {
            throw new BusinessException(ResultCode.TRIAGE_TOO_FREQUENT);
        }

        try {
            String answer = aiClient.chat(SYSTEM_PROMPT, symptoms);
            return TriageVO.of(answer);
        } catch (Exception e) {
            // 优雅降级：模型挂了不报 500，给一句友好提示。导诊是增值功能，不该拖垮患者体验
            log.warn("AI 导诊失败，优雅降级: {}", e.getMessage());
            return TriageVO.of(DEGRADE_ANSWER);
        }
    }

    /** 限流判断：只有明确 false（key 已存在）才是真频繁；null（异常边界）/抛异常都放行。 */
    private boolean isRateLimited(Long userId) {
        String lockKey = RedisKeyConstants.TRIAGE_LOCK_PREFIX + userId;
        try {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", RedisKeyConstants.TRIAGE_LOCK_TTL);
            return Boolean.FALSE.equals(locked);
        } catch (Exception e) {
            log.warn("导诊限流检查失败，跳过限流: {}", e.getMessage());
            return false;
        }
    }
}
