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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 对话客户端：调用 Python LangGraph 编排服务的对话接口。
 *
 * 前端消息 -> Java /api/chat -> Python Workflow 图（意图识别 -> 工具分发 -> 回复）
 * -> 工具节点反向调用 Java REST API 操控组件 -> 返回自然语言回复。
 *
 * 提供两种模式：
 * - {@link #chat} 非流式，一次性拿完整回复；
 * - {@link #streamChat} 流式，通过回调逐块推送 delta（供 SSE 转发）。
 *
 * 流式用 JDK 11 的 HttpClient + BodyHandlers.ofInputStream()，而不是 RestTemplate：
 * 前者拿到的是未缓冲的原始字节流，能真正做到"到一块推一块"；
 * RestTemplate 的响应体在部分实现下会被整体缓冲，流式效果就没了。
 */
@Slf4j
@Component
public class ChatClient {

    private final RestTemplate restTemplate;
    private final HttpClient httpClient;
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
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1) // SSE 在 HTTP/1.1 下兼容性最好
                .build();
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.baseUrl = baseUrl;
    }

    /** 非流式对话：一次性返回完整回复。 */
    public ChatResult chat(String message, List<ChatMessageDto> history) {
        Map<String, Object> payload = buildPayload(message, history);
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

    /**
     * 流式对话：逐块回调 delta，返回完整结果（intent + 拼好的 reply）。
     *
     * @param onIntent 收到意图时的回调（可为 null）
     * @param onDelta  收到增量文本时的回调（可为 null）
     */
    public ChatResult streamChat(String message, List<ChatMessageDto> history,
                                 Consumer<String> onIntent, Consumer<String> onDelta) {
        Map<String, Object> payload = buildPayload(message, history);
        String url = baseUrl + "/chat/stream";

        StringBuilder reply = new StringBuilder();
        String[] intentHolder = {"chat"};

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofMinutes(5)) // 流式总时长上限，远大于普通请求
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.error("Agent 流式接口返回非 200: status={}", response.statusCode());
                throw new ApiException(ErrorCode.AGENT_ERROR);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue; // 跳过空行与 SSE 注释
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(data);
                    String type = node.path("type").asText();

                    if ("intent".equals(type)) {
                        intentHolder[0] = node.path("intent").asText("chat");
                        if (onIntent != null) {
                            onIntent.accept(intentHolder[0]);
                        }
                    } else if ("delta".equals(type)) {
                        String content = node.path("content").asText();
                        if (!content.isEmpty()) {
                            reply.append(content);
                            if (onDelta != null) {
                                onDelta.accept(content);
                            }
                        }
                    } else if ("done".equals(type)) {
                        // 以 Agent 侧拼好的完整文本为准（覆盖 delta 丢失的情况）
                        String full = node.path("reply").asText("");
                        if (!full.isEmpty()) {
                            reply.setLength(0);
                            reply.append(full);
                        }
                        intentHolder[0] = node.path("intent").asText(intentHolder[0]);
                    } else if ("error".equals(type)) {
                        log.error("Agent 流式返回错误事件: {}", node.path("message").asText());
                        throw new ApiException(ErrorCode.AGENT_ERROR);
                    }
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("流式调用 Agent 失败: url={}", url, e);
            throw new ApiException(ErrorCode.AGENT_ERROR);
        }
        return new ChatResult(reply.toString(), intentHolder[0]);
    }

    /** 组装请求体：消息 + 历史 + 从设置读取的 LLM 配置（与每日总结链路一致）。 */
    private Map<String, Object> buildPayload(String message, List<ChatMessageDto> history) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("history", history == null ? List.of() : history);

        LlmSettingsDto llmSettings = settingsService.get();
        Map<String, Object> llmConfig = new LinkedHashMap<>();
        llmConfig.put("apiKey", llmSettings.getApiKey() == null ? "" : llmSettings.getApiKey());
        llmConfig.put("baseUrl", llmSettings.getBaseUrl() == null ? "" : llmSettings.getBaseUrl());
        llmConfig.put("model", llmSettings.getModel() == null ? "" : llmSettings.getModel());
        payload.put("llm_config", llmConfig);
        return payload;
    }
}
