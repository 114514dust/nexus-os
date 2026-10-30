package com.nexus.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.setting.LlmSettingsDto;
import com.nexus.setting.SettingsService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 灰区邮件的 LLM 精判。
 *
 * 只做两件事：判断「这封要不要我行动」并尽量提炼出一句可执行的待办 + 截止日。
 * 不负责抓取、不负责落库 —— 与每日总结一样，Java 是数据所有者，Python 只做判断。
 *
 * **失败必须静默降级**：Agent 服务没起、LLM key 没配、网络抖动，都只返回 empty，
 * 由调用方沿用规则结果。收信这条链路不能因为模型不可用就断掉。
 */
@Slf4j
@Component
public class MailLlmClient {

    /** 送进模型的正文字数上限：判定重要性不需要全文，长了只是在烧钱 */
    private static final int BODY_LIMIT = 2500;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final String baseUrl;

    public MailLlmClient(RestTemplateBuilder builder,
                         ObjectMapper objectMapper,
                         SettingsService settingsService,
                         @Value("${nexus.agent.base-url:http://127.0.0.1:9001}") String baseUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.baseUrl = baseUrl;
    }

    @Data
    public static class Verdict {
        private boolean important;
        private int score;
        private String reason;
        /** 提炼后的待办标题，可能为空 */
        private String todo;
        /** yyyy-MM-dd，可能为空 */
        private String dueDate;
    }

    public Optional<Verdict> classify(FetchedMail mail, int ruleScore, String ruleReason) {
        LlmSettingsDto llm = settingsService.get();
        // 没配 key 就别发请求了，省一次必然失败的往返
        if (llm.getApiKey() == null || llm.getApiKey().trim().isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject", mail.getSubject());
        payload.put("sender", mail.getSenderAddr());
        payload.put("received_at", mail.getReceivedAt() == null ? "" : mail.getReceivedAt().toString());
        payload.put("body", head(mail.getBody(), BODY_LIMIT));
        payload.put("rule_score", ruleScore);
        payload.put("rule_reason", ruleReason);
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("apiKey", llm.getApiKey());
        cfg.put("baseUrl", llm.getBaseUrl() == null ? "" : llm.getBaseUrl());
        cfg.put("model", llm.getModel() == null ? "" : llm.getModel());
        payload.put("llm_config", cfg);

        try {
            String resp = restTemplate.postForObject(baseUrl + "/classify_mail", payload, String.class);
            JsonNode node = objectMapper.readTree(resp);
            if (!node.path("ok").asBoolean(false)) {
                log.debug("[mail] LLM 精判不可用：{}", node.path("error").asText(""));
                return Optional.empty();
            }
            Verdict v = new Verdict();
            v.setImportant(node.path("important").asBoolean(false));
            v.setScore(node.path("score").asInt(ruleScore));
            v.setReason(node.path("reason").asText(""));
            v.setTodo(emptyToNull(node.path("todo").asText("")));
            v.setDueDate(emptyToNull(node.path("dueDate").asText("")));
            return Optional.of(v);
        } catch (Exception e) {
            // 连不上 Agent 是常态（只起 Java 也能用），降级到规则即可，不打堆栈
            log.debug("[mail] LLM 精判调用失败，降级为规则：{}", e.getMessage());
            return Optional.empty();
        }
    }

    public static LocalDate parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static String head(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
