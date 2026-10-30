package com.nexus.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞书自定义机器人渠道。
 *
 * 选型说明：只走「出站 webhook」，不接「应用机器人 + 事件回调」——
 * 后者要求公网可达的回调地址，本机部署没有公网 IP，走不通。
 */
@Component
@RequiredArgsConstructor
public class FeishuChannel implements NotifyChannel {

    private final RestTemplate notifyRestTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return "feishu";
    }

    @Override
    public void send(String text, NotifySettingsDto config) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        String secret = config.getSecret();
        // 机器人开启「签名校验」时才需要带上 timestamp + sign
        if (secret != null && !secret.trim().isEmpty()) {
            long timestamp = System.currentTimeMillis() / 1000;
            body.put("timestamp", String.valueOf(timestamp));
            body.put("sign", sign(timestamp, secret.trim()));
        }
        body.put("msg_type", "text");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", text);
        body.put("content", content);

        String resp = post(config.getWebhookUrl(), body);

        // 飞书 HTTP 200 也可能是业务失败（签名错、关键词不匹配、频率超限），必须解析返回码
        JsonNode node = objectMapper.readTree(resp);
        int code = node.has("code") ? node.path("code").asInt(-1) : node.path("StatusCode").asInt(-1);
        if (code != 0) {
            String msg = node.has("msg") ? node.path("msg").asText() : node.path("StatusMessage").asText();
            throw new IllegalStateException("飞书返回错误(code=" + code + ")：" + msg);
        }
    }

    private String post(String url, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        String resp = notifyRestTemplate.postForObject(url, new HttpEntity<>(json, headers), String.class);
        return resp == null ? "{}" : resp;
    }

    /**
     * 飞书签名算法：以 "{timestamp}\n{secret}" 为密钥，对空串做 HmacSHA256 后 Base64。
     */
    private String sign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[]{}));
    }
}
