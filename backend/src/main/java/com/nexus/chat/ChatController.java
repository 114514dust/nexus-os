package com.nexus.chat;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * AI 对话接口：基于 Workflow 编排的对话（意图识别 -> 工具分发 -> 回复）。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chatClient;

    @PostMapping
    public ApiResponse<ChatResult> chat(@Valid @RequestBody ChatRequest req) {
        return ApiResponse.ok(chatClient.chat(req.getMessage(), req.getHistory()));
    }
}
