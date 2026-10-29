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

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** 是否已被 Agent 消化处理，供记忆层使用 */
    @Column(nullable = false)
    private boolean processed;
}
