package com.nexus.mail;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邮箱消息接口。
 *
 * sync / probe 都用 POST：它们会去连外部 IMAP 服务器，属于「有副作用的动作」，
 * 不该被浏览器或中间层当成可缓存的 GET 预取。
 */
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    @GetMapping("/settings")
    public ApiResponse<MailSettingsDto> settings() {
        return ApiResponse.ok(mailService.settings());
    }

    @PutMapping("/settings")
    public ApiResponse<MailSettingsDto> saveSettings(@RequestBody MailSettingsDto dto) {
        return ApiResponse.ok(mailService.saveSettings(dto));
    }

    @PostMapping("/probe")
    public ApiResponse<MailSyncResult> probe() {
        return ApiResponse.ok(mailService.probe());
    }

    @PostMapping("/sync")
    public ApiResponse<MailSyncResult> sync() {
        return ApiResponse.ok(mailService.sync());
    }

    @GetMapping("/messages")
    public ApiResponse<List<MailView>> list(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(mailService.list(scope, keyword, limit));
    }

    @GetMapping("/messages/{id}")
    public ApiResponse<MailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(mailService.detail(id));
    }

    @PostMapping("/messages/{id}/todo")
    public ApiResponse<MailView> toTodo(@PathVariable Long id) {
        return ApiResponse.ok(mailService.toTodo(id));
    }

    @PostMapping("/messages/{id}/ignore")
    public ApiResponse<Void> ignore(@PathVariable Long id) {
        mailService.ignore(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/messages/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mailService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<MailStats> stats() {
        return ApiResponse.ok(mailService.stats());
    }
}
