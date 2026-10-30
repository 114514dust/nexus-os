package com.nexus.chat;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 对话记忆：AI 对话历史中的单条消息（user / assistant），持久化以支持跨会话上下文。
 */
@Data
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 消息角色：user / assistant */
    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 本轮意图标签（assistant 消息对应，可选） */
    @Column(length = 32)
    private String intent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
