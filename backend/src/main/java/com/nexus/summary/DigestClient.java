package com.nexus.summary;

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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 服务客户端：调用 Python LangGraph 编排服务的 /summarize 接口。
 *
 * Java 作为数据所有者，聚合当天数据后推给 Python，Python 只做「编排 + LLM」，
 * 返回总结文本，由 Java 落库。两者职责边界清晰。
 */
@Slf4j
@Component
public class DigestClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final String baseUrl;

    public DigestClient(RestTemplateBuilder builder,
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

    public DigestResult summarize(LocalDate date,
                                  List<Map<String, Object>> messages,
                                  List<Map<String, Object>> todos,
                                  List<Map<String, Object>> notes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", date.toString());
        payload.put("messages", messages);
        payload.put("todos", todos);
        payload.put("notes", notes);

        // 从设置读取 LLM 配置，透传给 Python 覆盖环境变量（支持前端动态配置 API Key）
        LlmSettingsDto llmSettings = settingsService.get();
        Map<String, Object> llmConfig = new LinkedHashMap<>();
        llmConfig.put("apiKey", llmSettings.getApiKey() == null ? "" : llmSettings.getApiKey());
        llmConfig.put("baseUrl", llmSettings.getBaseUrl() == null ? "" : llmSettings.getBaseUrl());
        llmConfig.put("model", llmSettings.getModel() == null ? "" : llmSettings.getModel());
        payload.put("llm_config", llmConfig);

        String url = baseUrl + "/summarize";
        try {
            String resp = restTemplate.postForObject(url, payload, String.class);
            JsonNode node = objectMapper.readTree(resp);
            String summary = node.path("summary").asText();
            String llmMode = node.path("llm").asText("rule-based");
            return new DigestResult(summary, llmMode);
        } catch (Exception e) {
            log.error("调用 Agent 服务失败: url={}", url, e);
            throw new ApiException(ErrorCode.AGENT_ERROR);
        }
    }
}
