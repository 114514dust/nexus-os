package com.nexus.subscription;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抓取器产出的标准化条目（与具体来源无关）。
 */
@Data
public class FetchedItem {

    /** 条目唯一标识（RSS 的 guid/link、B 站的 dynamic_id），用于去重 */
    private String externalId;
    private String title;
    private String link;
    private String content;
    /** 内容发布时间；来源没给则为 null */
    private LocalDateTime publishedAt;

    public FetchedItem(String externalId, String title, String link, String content) {
        this(externalId, title, link, content, null);
    }

    public FetchedItem(String externalId, String title, String link, String content,
                       LocalDateTime publishedAt) {
        this.externalId = externalId;
        this.title = title;
        this.link = link;
        this.content = content;
        this.publishedAt = publishedAt;
    }
}
