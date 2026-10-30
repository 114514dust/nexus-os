package com.nexus.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 校招岗位抓取器：从秋招网拉列表页并解析。
 *
 * 几个克制的设计：
 * 1. **只抓前几页**：这个组件的用途是「最近新开了哪些岗位」，不是做全站镜像；
 *    每页 30 条左右，抓 2 页足够覆盖近几天的更新，也对源站友好。
 * 2. **只用 GET + 普通 UA**：站点是公开列表页，没有登录态和反爬挑战，
 *    不需要模拟浏览器。反过来，一旦对方开始返回验证页，解析结果为空，
 *    任务记日志跳过即可，不会污染已有数据。
 * 3. **任何异常都不外抛**：抓取失败属于「今天没有新岗位」，
 *    不该让定时任务抛异常、更不该影响用户打开工作台。
 */
@Slf4j
@Component
public class JobPostingFetcher {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    /** 抓取最近 pages 页，页数上限 5，避免误配置把源站刷爆 */
    public List<JobPostingParser.Parsed> fetch(int pages) {
        int n = Math.max(1, Math.min(pages, 5));
        List<JobPostingParser.Parsed> all = new ArrayList<>();
        for (int page = 1; page <= n; page++) {
            String url = JobPostingParser.BASE_URL + "/?page=" + page;
            String html = get(url);
            if (html == null) {
                // 首页就失败说明源站不可达，后续页也没必要再试
                if (page == 1) {
                    return all;
                }
                break;
            }
            List<JobPostingParser.Parsed> parsed = JobPostingParser.parse(html);
            if (parsed.isEmpty()) {
                log.warn("[posting] 第 {} 页解析出 0 条，可能是页面结构变了", page);
                break;
            }
            all.addAll(parsed);
        }
        return all;
    }

    private String get(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("[posting] 抓取失败 {} -> HTTP {}", url, code);
                return null;
            }
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    sb.append(buf, 0, len);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("[posting] 抓取异常 {}: {}", url, e.toString());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
