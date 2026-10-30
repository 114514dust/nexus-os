package com.nexus.subscription;

import java.util.regex.Pattern;

/**
 * 把 feed 里的 HTML 摘要压成一行纯文本。
 *
 * RSS 的 description 通常是带标签的 HTML（甚至内嵌图片和脚本），
 * 直接塞进消息流会把卡片撑成一大坨。这里只保留文字本身。
 */
public final class HtmlText {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern BLANK = Pattern.compile("[\\s\\u00a0]+");

    private HtmlText() {
    }

    public static String clean(String html, int max) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = SCRIPT.matcher(html).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = decodeEntities(text);
        text = BLANK.matcher(text).replaceAll(" ").trim();
        if (max > 0 && text.length() > max) {
            text = text.substring(0, max).trim() + "…";
        }
        return text;
    }

    /**
     * 只解 HTML 实体，不去标签。
     * feed 标题有时带 `&apos;` 这类转义（Atom 里被双重转义过），
     * 直接显示出来就是「KDD&apos;26」。
     */
    public static String decode(String text) {
        return text == null ? "" : decodeEntities(text);
    }

    private static String decodeEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&hellip;", "…")
                .replace("&mdash;", "—");
    }
}
