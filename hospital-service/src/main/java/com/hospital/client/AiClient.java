package com.hospital.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 大模型客户端，基于 Spring AI 的 ChatClient 封装 DeepSeek。
 *
 * 只负责"调用一次"，不做业务判断、不降级：
 * 成功返回模型文本，失败把异常包成 RuntimeException 往上抛，
 * 降级是 TriageServiceImpl 的职责（AiClient 不越权决定"要不要兜底"）。
 *
 * 超时不再手写：由 spring.http.client.connect-timeout / read-timeout 控制；
 * 同时 spring.ai.retry.max-attempts 必须设成 1——Spring AI 默认重试 10 次，
 * 模型卡住时会把 Tomcat 线程占住十几秒，线程池被占满后连挂号接口都被拖垮。
 */
@Slf4j
@Component
public class AiClient {

    private final ChatClient chatClient;

    // ChatClient.Builder 由 Spring AI 自动配置提供，模型、key、超时全在 application.yml 里
    public AiClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 单轮对话：systemPrompt 定角色与约束，userPrompt 是患者症状。
     *
     * @return 模型返回的文本（我们要求它输出 JSON 字符串）
     */
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String text = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            return text == null ? "" : text;
        } catch (Exception e) {
            // 网络超时、401、5xx、响应解析失败都走这里，交给上层决定用关键词兜底
            log.warn("AI 导诊调用失败, cause={}", e.getMessage());
            throw new IllegalStateException("AI 调用失败", e);
        }
    }
}
