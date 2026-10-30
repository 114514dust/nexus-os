package com.nexus.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日邮件摘要推送。
 *
 * 时间定在 8:00，排在岗位抓取（7:30）之后、待办提醒（8:30）之前：
 * 「昨晚积了什么」应该先于「今天要做什么」被看到 —— 后者往往会因为前者多出几条。
 *
 * 三个开关都要开着才推：收信总开关、摘要开关、推送总开关（后者在 NotifyService 里判断）。
 * 少任何一个，都说明用户当前不想被这件事打扰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailDigestScheduler {

    private final MailSettingsService settingsService;
    private final MailDigestService digestService;

    @Scheduled(cron = "${nexus.mail.digest-cron:0 0 8 * * *}")
    public void daily() {
        try {
            MailSettingsDto cfg = settingsService.load();
            if (!cfg.isConfigured()) {
                return;
            }
            if (!cfg.isDigestEnabled()) {
                log.debug("[mail] 邮件摘要已关闭，跳过");
                return;
            }
            boolean pushed = digestService.pushIfAbsent(LocalDate.now().minusDays(1));
            log.debug("[mail] 每日摘要{}", pushed ? "已推送" : "无需推送");
        } catch (Exception e) {
            // 摘要失败不该影响任何主流程，记日志即可
            log.error("[mail] 每日摘要任务失败", e);
        }
    }
}
