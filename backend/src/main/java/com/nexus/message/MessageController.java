package com.nexus.message;

import com.nexus.common.ApiResponse;
import com.nexus.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 消息接口：通用 webhook 接收端点 + 分页列表。
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<PageResult<Message>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(messageService.list(page, size));
    }

    @PostMapping
    public ApiResponse<Message> receive(@Valid @RequestBody MessageCreateRequest req) {
        return ApiResponse.ok(messageService.receive(req));
    }
}
