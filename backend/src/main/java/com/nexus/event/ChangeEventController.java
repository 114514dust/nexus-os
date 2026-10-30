package com.nexus.event;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工作台数据变更事件流。
 *
 * 前端用 EventSource 订阅一次，之后任何数据变更（手动操作、AI 工具调用、
 * 定时抓取）都会通过它推送，各组件据此局部刷新。
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class ChangeEventController {

    private final ChangeEventService changeEventService;

    /** 订阅变更事件（SSE 长连接） */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return changeEventService.subscribe();
    }

    /** 当前订阅数，便于调试 */
    @GetMapping("/status")
    public ApiResponse<Integer> status() {
        return ApiResponse.ok(changeEventService.subscriberCount());
    }
}
