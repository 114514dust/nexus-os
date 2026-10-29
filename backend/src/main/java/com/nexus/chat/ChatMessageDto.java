package com.nexus.chat;

import lombok.Data;

/**
 * 对话历史中的单条消息（透传给 Python Agent，字段名与 OpenAI 消息格式对齐）。
 */
@Data
public class ChatMessageDto {

    private String role;

    private String content;
}
