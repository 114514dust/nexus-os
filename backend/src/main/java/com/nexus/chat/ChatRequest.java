package com.nexus.chat;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * AI 对话请求：当前消息 + 最近对话历史（多轮上下文）。
 */
@Data
public class ChatRequest {

    @NotBlank
    @Size(max = 2000)
    private String message;

    private List<ChatMessageDto> history;
}
