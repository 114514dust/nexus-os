package com.nexus.message;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 统一消息模型：来自各第三方 Adapter 的消息，先收拢，再由编排层去重/摘要/推送。
 */
@Data
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源（feishu/email/rss/...） */
    @Column(nullable = false, length = 32)
    private String source;

    /** 渠道（群/私聊/...），可选 */
    @Column(length = 64)
    private String channel;

    /** 发送者，可选 */
    @Column(length = 128)
    private String sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 标题：订阅类消息从 feed 条目带过来；普通消息为空，前端回退到 content 首行 */
    @Column(name = "item_title", length = 512)
    private String itemTitle;

    /** 原文链接：有值则前端整条可点开，不再让用户复制粘贴 URL */
    @Column(name = "item_link", length = 1024)
    private String itemLink;

    /** 是否已读（列名避开 H2 的 READ 关键字） */
    @Column(name = "msg_read", nullable = false)
    private boolean msgRead;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** 内容本身的发布时间（feed 的 pubDate）；为空表示来源没给 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 是否已被 Agent 消化处理，供记忆层使用 */
    @Column(nullable = false)
    private boolean processed;
}
