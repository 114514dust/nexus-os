package com.nexus.message;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息的展示模型。
 *
 * 为什么不直接返回实体：老数据的标题和链接都揉在 content 里（"标题\nURL"），
 * 前端拿到纯文本既点不开链接、也分不清标题和正文。
 * 这里统一做一次「拆分 + 兜底」，前端只负责渲染，不用重复写解析逻辑。
 */
@Data
public class MessageView {

    /** 从 content 里捞第一个 http(s) 链接，用于给老数据兜底 */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>）)]+");

    private Long id;
    private String source;
    /** 来源的中文短标签（RSS / B站 / 飞书 / 邮件…），前端直接显示 */
    private String sourceLabel;
    private String channel;
    private String sender;
    private String title;
    private String link;
    /** 正文摘要（不含 URL），优先用 feed 的 description */
    private String content;
    private LocalDateTime receivedAt;
    /** 内容发布时间，前端优先显示它；为空表示来源没给 */
    private LocalDateTime publishedAt;
    private boolean read;

    public static MessageView of(Message m) {
        MessageView v = new MessageView();
        v.id = m.getId();
        v.source = m.getSource();
        v.sourceLabel = label(m.getSource());
        v.channel = m.getChannel();
        v.sender = m.getSender();
        v.receivedAt = m.getReceivedAt();
        v.publishedAt = m.getPublishedAt();
        v.read = m.isMsgRead();

        String content = m.getContent() == null ? "" : m.getContent();
        String link = m.getItemLink();
        if (link == null || link.isEmpty()) {
            // 老数据：content 形如「标题\nhttps://...」
            Matcher matcher = URL_PATTERN.matcher(content);
            if (matcher.find()) {
                link = matcher.group();
            }
        }
        v.link = link;

        String title = m.getItemTitle();
        if (title == null || title.isEmpty()) {
            title = firstLine(content, link);
        }
        v.title = title;

        v.content = stripLink(content, link);
        return v;
    }

    /** content 去掉 URL 和标题后的剩余部分，用作摘要；没有就留空 */
    private static String stripLink(String content, String link) {
        String rest = content;
        if (link != null && !link.isEmpty()) {
            rest = rest.replace(link, " ");
        }
        rest = rest.replaceAll("https?://\\S+", " ").trim();
        // content 里第一行通常就是标题，已经从 title 显示，正文里去掉避免重复
        String[] lines = rest.split("\\R");
        if (lines.length > 1 && !lines[0].trim().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) {
                    sb.append(lines[i].trim()).append('\n');
                }
            }
            if (sb.length() > 0) {
                return sb.toString().trim();
            }
        }
        return "";
    }

    private static String firstLine(String content, String link) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (link != null && trimmed.startsWith(link)) {
                continue;
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                continue;
            }
            return trimmed;
        }
        return content.isEmpty() ? "(无标题)" : content;
    }

    public static String label(String source) {
        if (source == null) {
            return "其他";
        }
        switch (source) {
            case "rss":
                return "RSS";
            case "bilibili":
                return "B站";
            case "feishu":
                return "飞书";
            case "email":
                return "邮件";
            case "wechat":
                return "微信";
            case "agent":
                return "AI";
            default:
                return source;
        }
    }
}
