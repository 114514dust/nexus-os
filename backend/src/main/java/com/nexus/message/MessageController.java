package com.nexus.message;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 消息接口：通用 webhook 接收端点 + 收件箱查询 / 已读标记。
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<MessagePage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "false") boolean unread) {
        return ApiResponse.ok(messageService.list(page, size, source, unread));
    }

    @PostMapping
    public ApiResponse<Message> receive(@Valid @RequestBody MessageCreateRequest req) {
        return ApiResponse.ok(messageService.receive(req));
    }

    /** 标记单条已读/未读 */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                      @RequestParam(defaultValue = "true") boolean read) {
        messageService.markRead(id, read);
        return ApiResponse.ok();
    }

    /** 全部标记已读，可按来源只清一类 */
    @PostMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@RequestParam(required = false) String source) {
        return ApiResponse.ok(messageService.markAllRead(source));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return ApiResponse.ok();
    }
}
