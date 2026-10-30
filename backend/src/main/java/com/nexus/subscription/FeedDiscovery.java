package com.nexus.subscription;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 订阅地址自动识别。
 *
 * 用户手上通常只有一个网址——有时是 RSS 地址，更多时候是博客首页（他们根本不知道
 * RSS 在哪里）。这里做两件事：
 *   1. 直接把地址当 feed 解析；
 *   2. 解析不了就当网页抓下来，从 {@code <link rel="alternate" type="application/rss+xml">}
 *      里找官方声明的订阅地址（这是 RSS 的标准自动发现机制）。
 * 两条都不通才判定失败。
 */
@Slf4j
@Component
public class FeedDiscovery {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REL_ALTERNATE = Pattern.compile("rel\\s*=\\s*[\"']?alternate", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_FEED = Pattern.compile(
            "type\\s*=\\s*[\"']?application/(rss|atom)\\+xml", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    public FeedDiscovery(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(6))
                .setReadTimeout(Duration.ofSeconds(12))
                .build();
    }

    /** 识别成功的结果 */
    public static class Resolved {

        private final String feedUrl;
        private final String title;
        private final int sampleCount;

        public Resolved(String feedUrl, String title, int sampleCount) {
            this.feedUrl = feedUrl;
            this.title = title;
            this.sampleCount = sampleCount;
        }

        public String getFeedUrl() {
            return feedUrl;
        }

        public String getTitle() {
            return title;
        }

        public int getSampleCount() {
            return sampleCount;
        }
    }

    /** 解析失败返回 null，由调用方给出友好提示 */
    public Resolved resolve(String rawUrl) {
        String url = normalize(rawUrl);
        if (url == null) {
            return null;
        }
        // 1. 直接当 feed（最常见，一步到位）
        Resolved direct = tryFeed(url);
        if (direct != null) {
            return direct;
        }

        // 2. 当网页：拿它声明的订阅地址；拿不到再猜常见后缀
        Set<String> candidates = new LinkedHashSet<>();
        String html = fetchText(url);
        if (html != null) {
            candidates.addAll(findFeedLinks(html, url));
        }
        candidates.addAll(commonSuffixes(url));

        // 并发探测：声明的地址有时是被墙的第三方（如 feedburner），
        // 串行等它超时会让用户干等十几秒，所以一起发出去取最先成功的那个。
        return firstSuccessful(new ArrayList<>(candidates));
    }

    private Resolved firstSuccessful(List<String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, candidates.size()), r -> {
            Thread t = new Thread(r, "feed-discovery");
            t.setDaemon(true);
            return t;
        });
        try {
            CompletionService<Resolved> cs = new ExecutorCompletionService<>(pool);
            for (String candidate : candidates) {
                cs.submit(() -> tryFeed(candidate));
            }
            for (int i = 0; i < candidates.size(); i++) {
                Future<Resolved> future = cs.poll(8, TimeUnit.SECONDS);
                if (future == null) {
                    break;
                }
                Resolved resolved = future.get();
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("并发探测订阅地址失败：{}", e.getMessage());
            return null;
        } finally {
            pool.shutdownNow();
        }
    }

    /** 猜常见订阅路径：/atom.xml、/feed、/rss.xml… 国内博客命中率很高 */
    static List<String> commonSuffixes(String url) {
        List<String> result = new ArrayList<>();
        String base;
        try {
            URI uri = URI.create(url);
            String origin = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            String path = uri.getPath() == null ? "/" : uri.getPath();
            if (!path.endsWith("/")) {
                int slash = path.lastIndexOf('/');
                path = slash >= 0 ? path.substring(0, slash + 1) : "/";
            }
            base = origin + path;
        } catch (Exception e) {
            return result;
        }
        String[] names = {"atom.xml", "feed", "rss.xml", "index.xml", "feed.xml", "rss"};
        for (String name : names) {
            result.add(base + name);
        }
        return result;
    }

    /** 补协议、去空白；明显不是网址时返回 null */
    public static String normalize(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.isEmpty()) {
            return null;
        }
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            // 「abcdef」这种输入补上协议后能过 URI 校验，但没有点号的「域名」一定是写错了
            if (host == null || host.isEmpty()) {
                return null;
            }
            if (!host.contains(".") && !"localhost".equalsIgnoreCase(host)) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        return url;
    }

    private Resolved tryFeed(String url) {
        try {
            byte[] bytes = fetchBytes(url);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(new ByteArrayInputStream(bytes)));
            List<?> entries = feed.getEntries();
            // 标题为空说明大概率不是 feed（HTML 被硬解析时也会「成功」，但拿不到标题）
            String title = feed.getTitle() != null ? feed.getTitle().trim() : "";
            if (title.isEmpty() && entries.isEmpty()) {
                return null;
            }
            return new Resolved(url, title, entries.size());
        } catch (Exception e) {
            log.debug("不是有效的 feed：{}（{}）", url, e.getMessage());
            return null;
        }
    }

    private byte[] fetchBytes(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, UA);
            headers.set(HttpHeaders.ACCEPT, "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.5");
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            return resp.getBody();
        } catch (Exception e) {
            log.debug("抓取失败：{}（{}）", url, e.getMessage());
            return null;
        }
    }

    private String fetchText(String url) {
        byte[] bytes = fetchBytes(url);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 从 HTML 里提取所有声明为 rss/atom 的地址（相对路径转绝对） */
    static List<String> findFeedLinks(String html, String baseUrl) {
        Set<String> found = new LinkedHashSet<>();
        Matcher tags = LINK_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            if (!TYPE_FEED.matcher(tag).find()) {
                continue;
            }
            // rel 缺省也当候选：不少站点只写 type
            if (tag.toLowerCase().contains("rel=") && !REL_ALTERNATE.matcher(tag).find()) {
                continue;
            }
            Matcher href = HREF.matcher(tag);
            if (href.find()) {
                String abs = absolutize(href.group(1), baseUrl);
                if (abs != null) {
                    found.add(abs);
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static String absolutize(String href, String baseUrl) {
        try {
            return new URL(new URL(baseUrl), href).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
