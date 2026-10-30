package com.nexus.chat;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * AI 对话请求：当前消息。历史由后端从持久化的对话记忆读取，前端无需再传。
 */
@Data
public class ChatRequest {

    @NotBlank
    @Size(max = 2000)
    private String message;
}
