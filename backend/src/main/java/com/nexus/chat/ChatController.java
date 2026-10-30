package com.nexus.chat;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.List;

/**
 * AI 对话接口：基于 Workflow 编排的对话（意图识别 -> 工具分发 -> 回复），历史持久化。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 发起对话（后端自动携带最近历史并落库） */
    @PostMapping
    public ApiResponse<ChatResult> chat(@Valid @RequestBody ChatRequest req) {
        return ApiResponse.ok(chatService.chat(req.getMessage()));
    }

    /**
     * 流式对话（SSE）：事件类型 intent / delta / done / error。
     *
     * 返回 SseEmitter 后请求线程即释放，实际推送在 ChatService 的线程池里进行。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest req) {
        return chatService.streamChat(req.getMessage());
    }

    /** 查询对话历史（时间正序，最近 limit 条） */
    @GetMapping("/history")
    public ApiResponse<List<ChatMessage>> history(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(chatService.history(limit));
    }

    /** 清空对话记忆 */
    @DeleteMapping("/history")
    public ApiResponse<Void> clear() {
        chatService.clear();
        return ApiResponse.ok();
    }
}
