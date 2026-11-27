package com.hospital.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.client.AiClient;
import com.hospital.client.TriageScheduleHolder;
import com.hospital.common.constant.RedisKeyConstants;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.triage.TriageAskDTO;
import com.hospital.service.TriageService;
import com.hospital.vo.triage.TriageScheduleVO;
import com.hospital.vo.triage.TriageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 导诊实现（Agent 版 + 会话记忆）。
 *
 * 流程：限流 → 读 Redis 历史 → 调 Agent（带历史上下文）→ 写 Redis 历史。
 * 模型挂掉时优雅降级——给一句友好提示，而不是报 500。
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

    /** SSE 流中号源数据的标记前缀，前端据此区分文本和结构化数据 */
    public static final String SCHEDULES_PREFIX = "[SCHEDULES]";

    private final AiClient aiClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final TriageScheduleHolder scheduleHolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TriageVO ask(TriageAskDTO dto, Long userId) {
        String symptoms = dto.getSymptoms().trim();

        if (isRateLimited(userId)) {
            throw new BusinessException(ResultCode.TRIAGE_TOO_FREQUENT);
        }

        scheduleHolder.clear();
        try {
            List<Message> history = getHistory(userId);
            String answer = aiClient.chatWithHistory(SYSTEM_PROMPT, history, symptoms);
            saveHistory(userId, symptoms, answer);
            return TriageVO.of(answer, scheduleHolder.take());
        } catch (Exception e) {
            log.warn("AI 导诊失败，优雅降级: {}", e.getMessage());
            return TriageVO.of(DEGRADE_ANSWER, List.of());
        } finally {
            scheduleHolder.clear();
        }
    }

    @Override
    public Flux<String> askStream(TriageAskDTO dto, Long userId) {
        String symptoms = dto.getSymptoms().trim();
        if (isRateLimited(userId)) {
            throw new BusinessException(ResultCode.TRIAGE_TOO_FREQUENT);
        }
        scheduleHolder.clear();
        return aiClient.chatStream(SYSTEM_PROMPT, symptoms)
                .doOnError(e -> log.warn("AI 流式导诊失败: {}", e.getMessage()))
                .onErrorResume(e -> Flux.just(DEGRADE_ANSWER))
                .concatWith(Flux.defer(() -> {
                    List<TriageScheduleVO> schedules = scheduleHolder.take();
                    if (schedules.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.just(SCHEDULES_PREFIX + toJson(schedules));
                }))
                .doFinally(signal -> scheduleHolder.clear());
    }

    /** 从 Redis 读历史消息，转成 Spring AI Message 列表 */
    private List<Message> getHistory(Long userId) {
        String key = RedisKeyConstants.TRIAGE_HISTORY_PREFIX + userId;
        try {
            List<String> raw = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<Message> messages = new ArrayList<>();
            for (String json : raw) {
                HistoryMessage msg = objectMapper.readValue(json, HistoryMessage.class);
                if ("user".equals(msg.role)) {
                    messages.add(new UserMessage(msg.content));
                } else if ("assistant".equals(msg.role)) {
                    messages.add(new AssistantMessage(msg.content));
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("读取会话记忆失败，按无历史处理: {}", e.getMessage());
            return List.of();
        }
    }

    /** 把本轮问答写入 Redis List，保留最近 N 轮 */
    private void saveHistory(Long userId, String userMessage, String assistantMessage) {
        String key = RedisKeyConstants.TRIAGE_HISTORY_PREFIX + userId;
        try {
            stringRedisTemplate.opsForList().rightPush(key,
                    objectMapper.writeValueAsString(new HistoryMessage("user", userMessage)));
            stringRedisTemplate.opsForList().rightPush(key,
                    objectMapper.writeValueAsString(new HistoryMessage("assistant", assistantMessage)));
            // 只保留最近 N 轮（2N 条消息），截断旧消息
            long size = stringRedisTemplate.opsForList().size(key);
            int maxMessages = RedisKeyConstants.TRIAGE_HISTORY_MAX_ROUNDS * 2;
            if (size > maxMessages) {
                stringRedisTemplate.opsForList().trim(key, size - maxMessages, -1);
            }
            stringRedisTemplate.expire(key, RedisKeyConstants.TRIAGE_HISTORY_TTL);
        } catch (Exception e) {
            log.warn("写入会话记忆失败: {}", e.getMessage());
        }
    }

    private String toJson(List<TriageScheduleVO> schedules) {
        try {
            return objectMapper.writeValueAsString(schedules);
        } catch (Exception e) {
            log.warn("号源序列化失败: {}", e.getMessage());
            return "[]";
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

    /** Redis 里存的历史消息格式 */
    private static class HistoryMessage {
        public String role;
        public String content;

        public HistoryMessage() {
        }

        public HistoryMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
