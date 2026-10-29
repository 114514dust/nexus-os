package com.nexus.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.setting.LlmSettingsDto;
import com.nexus.setting.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 对话客户端：调用 Python LangGraph 编排服务的 /chat 接口。
 *
 * 前端消息 -> Java /api/chat -> Python Workflow 图（意图识别 -> 工具分发 -> 回复）
 * -> 工具节点反向调用 Java REST API 操控组件 -> 返回自然语言回复。
 */
@Slf4j
@Component
public class ChatClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final String baseUrl;

    public ChatClient(RestTemplateBuilder builder,
                      ObjectMapper objectMapper,
                      SettingsService settingsService,
                      @Value("${nexus.agent.base-url:http://127.0.0.1:9001}") String baseUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.baseUrl = baseUrl;
    }

    public ChatResult chat(String message, List<ChatMessageDto> history) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("history", history == null ? List.of() : history);

        // 从设置读取 LLM 配置透传给 Python（与每日总结链路一致）
        LlmSettingsDto llmSettings = settingsService.get();
        Map<String, Object> llmConfig = new LinkedHashMap<>();
        llmConfig.put("apiKey", llmSettings.getApiKey() == null ? "" : llmSettings.getApiKey());
        llmConfig.put("baseUrl", llmSettings.getBaseUrl() == null ? "" : llmSettings.getBaseUrl());
        llmConfig.put("model", llmSettings.getModel() == null ? "" : llmSettings.getModel());
        payload.put("llm_config", llmConfig);

        String url = baseUrl + "/chat";
        try {
            String resp = restTemplate.postForObject(url, payload, String.class);
            JsonNode node = objectMapper.readTree(resp);
            String reply = node.path("reply").asText();
            String intent = node.path("intent").asText("chat");
            return new ChatResult(reply, intent);
        } catch (Exception e) {
            log.error("调用 Agent 对话服务失败: url={}", url, e);
            throw new ApiException(ErrorCode.AGENT_ERROR);
        }
    }
}
