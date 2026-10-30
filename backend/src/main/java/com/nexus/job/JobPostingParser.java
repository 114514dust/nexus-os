package com.nexus.job;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 秋招网（qiuzhaowang.com）列表页解析器。
 *
 * 站点是服务端渲染的静态 HTML，但把结构化字段都放进了 `<article class="job-row">` 的
 * `data-job-*` 属性里（company / city / cohort / batch / deadline / 公告链接…），
 * 所以不需要引入 HTML 解析库，按属性抽取即可 —— 少一个依赖，也少一处版本兼容风险。
 *
 * 解析必须是「失败即空」的：站点一改版，这里拿到空列表，抓取任务记日志退出，
 * 不能让解析异常冒到调用方把整个定时任务打挂。
 */
public final class JobPostingParser {

    public static final String SOURCE = "qiuzhaowang";
    public static final String BASE_URL = "https://qiuzhaowang.com";

    /** 一条 job-row 的属性都在起始标签里 */
    private static final Pattern ROW = Pattern.compile("<article[^>]*class=\"job-row\"[^>]*>");
    private static final Pattern TIME = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private JobPostingParser() {
    }

    /** 解析结果（未落库的原始字段） */
    public static class Parsed {
        public String sourceId;
        public String company;
        public String title;
        public String positions;
        public String city;
        public String cohort;
        public String batch;
        public String companyType;
        public String detailUrl;
        public String announceUrl;
        public String deadlineText;
        public LocalDate publishedAt;
    }

    public static List<Parsed> parse(String html) {
        List<Parsed> out = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return out;
        }
        Matcher m = ROW.matcher(html);
        while (m.find()) {
            String tag = m.group();
            Parsed p = new Parsed();
            String href = attr(tag, "data-job-href");
            p.sourceId = extractSourceId(href, attr(tag, "data-job-id"));
            p.company = attr(tag, "data-job-company");
            p.title = attr(tag, "data-job-title");
            p.positions = attr(tag, "data-job-title-details");
            p.city = attr(tag, "data-job-city");
            p.cohort = attr(tag, "data-job-cohort");
            p.batch = attr(tag, "data-job-batch");
            p.companyType = attr(tag, "data-job-company-type");
            p.deadlineText = attr(tag, "data-job-deadline");
            p.announceUrl = attr(tag, "data-job-announcement-url");

            if (href != null && !href.isEmpty()) {
                p.detailUrl = href.startsWith("http") ? href : BASE_URL + href;
            }
            if (p.company == null || p.company.trim().isEmpty() || p.sourceId == null) {
                // 缺公司名或唯一 id 的记录没有去重依据，直接丢弃
                continue;
            }
            p.publishedAt = parseDate(tag, attr(tag, "data-job-update"));
            out.add(p);
        }
        return out;
    }

    /**
     * 去重键取站内 position 数字 id，而不是 data-job-id ——
     * 后者形如「uuid:来源-33978」，中间夹着中文公司名，长度能到 147 字符，
     * 既撑爆字段又没法用（公司改名就变了）。数字 id 来自详情页 URL，稳定且短。
     */
    private static String extractSourceId(String href, String rawId) {
        if (href != null) {
            Matcher m = Pattern.compile("position-(\\d+)").matcher(href);
            if (m.find()) {
                return m.group(1);
            }
        }
        if (rawId != null && !rawId.isEmpty()) {
            Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(rawId);
            if (m.find()) {
                return m.group(1);
            }
            return rawId.length() > 120 ? rawId.substring(rawId.length() - 120) : rawId;
        }
        return null;
    }

    /**
     * 优先用 `<time datetime>`（ISO 时间，UTC），转成东八区再取日期；
     * 回退到「10月30日」这类文案，补当年。跨年时文案会差一年，
     * 所以只在拿不到 datetime 时才用。
     */
    private static LocalDate parseDate(String tag, String updateText) {
        Matcher tm = TIME.matcher(tag);
        if (tm.find()) {
            try {
                return Instant.parse(tm.group(1)).atZone(ZONE).toLocalDate();
            } catch (Exception ignored) {
                // 落到文案解析
            }
        }
        if (updateText != null) {
            Matcher dm = Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(updateText);
            if (dm.find()) {
                LocalDate today = LocalDate.now(ZONE);
                int month = Integer.parseInt(dm.group(1));
                int day = Integer.parseInt(dm.group(2));
                LocalDate d = LocalDate.of(today.getYear(), month, day);
                // 抓的是「未来的月份」说明跨年了（比如 1 月抓到 12 月的公告）
                return d.isAfter(today.plusDays(1)) ? d.minusYears(1) : d;
            }
        }
        return LocalDate.now(ZONE);
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? unescape(m.group(1)).trim() : null;
    }

    /** HTML 属性里的实体，主要是 &amp;（公告链接带查询参数时很常见） */
    private static String unescape(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        return s.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }
}
