package com.hospital.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 大模型 HTTP 客户端，封装 DeepSeek 的 Anthropic 兼容接口。
 *
 * 只负责"调用一次"，不做业务判断、不降级：
 * 成功返回模型文本，失败把 checked 异常包成 RuntimeException 往上抛，
 * 降级是 TriageServiceImpl 的职责（AiClient 不越权决定"要不要兜底"）。
 *
 * 超时为什么设在这里：一个请求占一个 Tomcat 线程，模型卡住会拖着线程不干活，
 * 线程池被占满后连挂号接口都被拖垮——所以必须设超时，而不是等它自然返回。
 */
@Slf4j
@Component
public class AiClient {

    /** 连不上模型最多等 2 秒 */
    private static final int CONNECT_TIMEOUT_MS = 2000;

    /** 模型正常 2~3 秒返回，读超时给 10 秒余量 */
    private static final int READ_TIMEOUT_MS = 10000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public AiClient(ObjectMapper objectMapper,
                    @Value("${hospital.ai.base-url}") String baseUrl,
                    @Value("${hospital.ai.api-key}") String apiKey,
                    @Value("${hospital.ai.model}") String model) {
        this.objectMapper = objectMapper;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        // Anthropic 协议用 x-api-key + anthropic-version，和 OpenAI 的 Bearer 不是一套
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    /**
     * 单轮对话：systemPrompt 定角色与约束，userPrompt 是患者症状。
     *
     * @return 模型返回的文本（我们要求它输出 JSON 字符串）
     */
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 500,
                "temperature", 0,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)));

        try {
            //jackson 的 objectMapper.writeValueAsString() 把它序列化成文本
            String json = objectMapper.writeValueAsString(body);
            String resp = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            // Anthropic 响应：content 是数组，取第一段的 text
            JsonNode root = objectMapper.readTree(resp);
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText("");
            }
            return "";
        } catch (Exception e) {
            log.warn("AI 导诊调用失败, model={}, cause={}", model, e.getMessage());
            throw new IllegalStateException("AI 调用失败", e);
        }
    }
}
