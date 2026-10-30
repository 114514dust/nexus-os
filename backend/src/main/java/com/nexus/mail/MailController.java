package com.nexus.mail;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 邮箱消息接口。
 *
 * sync / probe 都用 POST：它们会去连外部 IMAP 服务器，属于「有副作用的动作」，
 * 不该被浏览器或中间层当成可缓存的 GET 预取。附件下载同理（首次下载会连邮箱）。
 */
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;
    private final MailDigestService digestService;
    private final MailAttachmentService attachmentService;
    private final SenderRuleService ruleService;

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

    // ---- 每日摘要 ----

    /** 预览某天的摘要（默认昨天）。只读，不推送 */
    @GetMapping("/digest")
    public ApiResponse<MailDigest> digest(@RequestParam(required = false) String date) {
        return ApiResponse.ok(digestService.build(parseDate(date)));
    }

    /**
     * 立即推送一次摘要。返回是否真的推了 ——
     * 没有要紧邮件时不推，用户需要知道这是「没东西可推」而不是「推送坏了」。
     */
    @PostMapping("/digest/push")
    public ApiResponse<DigestPushResult> pushDigest(@RequestParam(required = false) String date) {
        boolean pushed = digestService.push(parseDate(date));
        DigestPushResult r = new DigestPushResult();
        r.setPushed(pushed);
        r.setMessage(pushed ? "摘要已推送" : "这一天没有要紧邮件，未推送");
        return ApiResponse.ok(r);
    }

    // ---- 附件 ----

    /**
     * 下载附件。首次访问会连一次 IMAP 把字节取回来存本地，之后直接读盘。
     * inline=true 用于图片/PDF 直接在看板里预览。
     */
    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                            @RequestParam(defaultValue = "false") boolean inline) {
        MailAttachmentService.Payload payload = attachmentService.open(id);
        ContentDisposition disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(payload.getFilename(), StandardCharsets.UTF_8)
                .build();
        MediaType type;
        try {
            type = MediaType.parseMediaType(payload.getContentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(type)
                .body(new FileSystemResource(payload.getPath()));
    }

    // ---- 发件人规则 ----

    @GetMapping("/rules")
    public ApiResponse<List<SenderRule>> rules() {
        return ApiResponse.ok(ruleService.all());
    }

    @PostMapping("/rules")
    public ApiResponse<SenderRule> createRule(@RequestBody SenderRuleRequest req) {
        return ApiResponse.ok(ruleService.create(req.getPattern(), req.getAction(), req.getNote()));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<SenderRule> updateRule(@PathVariable Long id, @RequestBody SenderRuleRequest req) {
        return ApiResponse.ok(ruleService.update(id, req.getPattern(), req.getAction(), req.getNote()));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        ruleService.delete(id);
        return ApiResponse.ok();
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return LocalDate.now().minusDays(1);
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            // 参数错就是参数错，别默默退回昨天让人以为数据不对
            throw new com.nexus.common.ApiException(com.nexus.common.ErrorCode.BAD_REQUEST,
                    "日期格式应为 yyyy-MM-dd");
        }
    }

    @lombok.Data
    public static class DigestPushResult {
        private boolean pushed;
        private String message;
    }
}
