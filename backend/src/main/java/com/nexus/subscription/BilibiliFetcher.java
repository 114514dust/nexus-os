package com.nexus.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * B 站 UP 主动态抓取器。
 *
 * 说明：B 站动态接口（feed/space）要求 wbi 签名（w_rid + wts），本实现完整还原签名算法。
 * 但 B 站对无 Cookie 的高频访问有风控（-352/-403），抓取失败会优雅降级为空列表并记录日志，
 * 不影响 RSS 等其他订阅源。
 */
@Slf4j
@Component
public class BilibiliFetcher implements FeedFetcher {

    private static final String NAV_URL = "https://api.bilibili.com/x/web-interface/nav";
    private static final String FEED_SPACE_URL = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** wbi 混排索引表（自 wbi 机制上线以来未变） */
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
            26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
            20, 52, 44, 34
    };

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedImgKey;
    private volatile String cachedSubKey;
    private volatile long cachedAt;

    public BilibiliFetcher(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "bilibili";
    }

    @Override
    public List<FetchedItem> fetch(Subscription subscription) {
        String hostMid = subscription.getUrl().trim();
        String[] keys = getWbiKeys();
        if (keys == null) {
            return Collections.emptyList();
        }
        String mixinKey = getMixinKey(keys[0], keys[1]);

        Map<String, String> params = new TreeMap<>();
        params.put("host_mid", hostMid);
        params.put("offset", "");
        params.put("timezone_offset", "-480");
        params.put("web_location", "333.1368");
        long wts = System.currentTimeMillis() / 1000;
        params.put("wts", String.valueOf(wts));

        StringBuilder url = new StringBuilder(FEED_SPACE_URL).append('?');
        for (Map.Entry<String, String> e : params.entrySet()) {
            url.append(e.getKey()).append('=').append(urlEncode(e.getValue())).append('&');
        }
        url.append("w_rid=").append(sign(params, mixinKey));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, UA);
            headers.set(HttpHeaders.REFERER, "https://space.bilibili.com/");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url.toString(), HttpMethod.GET, entity, String.class);
            JsonNode node = objectMapper.readTree(resp.getBody());
            int code = node.path("code").asInt(-1);
            if (code != 0) {
                log.warn("B站动态抓取失败: code={}, message={}", code, node.path("message").asText());
                return Collections.emptyList();
            }
            return parseItems(node.path("data").path("items"));
        } catch (Exception e) {
            log.error("B站动态抓取异常: uid={}", hostMid, e);
            return Collections.emptyList();
        }
    }

    // ---- wbi 签名 ----

    private String[] getWbiKeys() {
        if (cachedImgKey != null && System.currentTimeMillis() - cachedAt < 3_600_000) {
            return new String[]{cachedImgKey, cachedSubKey};
        }
        try {
            String nav = restTemplate.getForObject(NAV_URL, String.class);
            JsonNode wbi = objectMapper.readTree(nav).path("data").path("wbi_img");
            cachedImgKey = extractKey(wbi.path("img_url").asText());
            cachedSubKey = extractKey(wbi.path("sub_url").asText());
            cachedAt = System.currentTimeMillis();
            return new String[]{cachedImgKey, cachedSubKey};
        } catch (Exception e) {
            log.error("获取 B 站 wbi keys 失败", e);
            return null;
        }
    }

    private String extractKey(String url) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String getMixinKey(String imgKey, String subKey) {
        String raw = imgKey + subKey;
        StringBuilder sb = new StringBuilder(32);
        for (int idx : MIXIN_KEY_ENC_TAB) {
            sb.append(raw.charAt(idx));
        }
        return sb.toString();
    }

    private String sign(Map<String, String> params, String mixinKey) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String value = e.getValue().replaceAll("[!'()*]", "");
            query.append(e.getKey()).append('=').append(urlEncode(value)).append('&');
        }
        query.deleteCharAt(query.length() - 1);
        return md5(query + mixinKey);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    // ---- 解析 ----

    private List<FetchedItem> parseItems(JsonNode itemsNode) {
        List<FetchedItem> items = new ArrayList<>();
        if (itemsNode == null || !itemsNode.isArray()) {
            return items;
        }
        for (JsonNode item : itemsNode) {
            String dynamicId = item.path("id_str").asText(item.path("id").asText(""));
            if (dynamicId.isEmpty()) {
                continue;
            }
            String text = extractText(item);
            String link = "https://t.bilibili.com/" + dynamicId;
            String title = text.isEmpty() ? "(动态)" : text;
            items.add(new FetchedItem(dynamicId, title, link, text));
        }
        return items;
    }

    private String extractText(JsonNode item) {
        StringBuilder sb = new StringBuilder();
        JsonNode desc = item.path("modules").path("module_dynamic").path("desc").path("text");
        if (desc.isTextual()) {
            sb.append(desc.asText());
        }
        JsonNode major = item.path("modules").path("module_dynamic").path("major");
        String archiveTitle = major.path("archive").path("title").asText("");
        String opusTitle = major.path("opus").path("title").asText("");
        if (!archiveTitle.isEmpty() && !sb.toString().contains(archiveTitle)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("【视频】").append(archiveTitle);
        } else if (!opusTitle.isEmpty() && !sb.toString().contains(opusTitle)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("【图文】").append(opusTitle);
        }
        return sb.toString().trim();
    }
}
