package com.nexus.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信群机器人渠道。
 */
@Component
@RequiredArgsConstructor
public class WecomChannel implements NotifyChannel {

    private final RestTemplate notifyRestTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "wecom";
    }

    @Override
    public void send(String text, NotifySettingsDto config) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("content", text);
        body.put("text", content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        String resp = notifyRestTemplate.postForObject(
                config.getWebhookUrl(), new HttpEntity<>(json, headers), String.class);

        JsonNode node = objectMapper.readTree(resp == null ? "{}" : resp);
        int errcode = node.path("errcode").asInt(-1);
        if (errcode != 0) {
            throw new IllegalStateException(
                    "企业微信返回错误(errcode=" + errcode + ")：" + node.path("errmsg").asText());
        }
    }
}
