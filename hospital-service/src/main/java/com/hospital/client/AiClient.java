package com.hospital.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 大模型客户端，基于 Spring AI 的 ChatClient 封装 DeepSeek。
 *
 * 只负责"调用一次"，不做业务判断、不降级：
 * 成功返回模型文本，失败把异常包成 RuntimeException 往上抛，
 * 降级是 TriageServiceImpl 的职责（AiClient 不越权决定"要不要兜底"）。
 *
 * 工具通过 defaultTools 挂到 ChatClient 上：模型会自己决定调哪个工具、调几次，
 * 边查边答——这就是从"单轮问答"到"Agent"的核心区别。
 */
@Slf4j
@Component
public class AiClient {

    private final ChatClient chatClient;

    // ChatModel 由 Spring AI 自动配置提供（AnthropicChatModel）；triageTools 是导诊的工具集
    public AiClient(ChatModel chatModel, TriageTools triageTools) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultTools(triageTools)
                .build();
    }

    /**
     * 单轮对话入口：systemPrompt 定角色与约束，userPrompt 是患者症状。
     * 模型会根据 systemPrompt 里的引导，自动调用挂载的工具完成导诊。
     *
     * @return 模型最终返回给患者的自然语言回答
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
            // 网络超时、401、5xx、响应解析失败都走这里，交给上层做优雅降级
            log.warn("AI 导诊调用失败, cause={}", e.getMessage());
            throw new IllegalStateException("AI 调用失败", e);
        }
    }
}
