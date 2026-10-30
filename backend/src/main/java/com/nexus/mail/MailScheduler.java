package com.nexus.mail;

import com.nexus.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 邮箱定时收取。
 *
 * 频率定在 15 分钟一次：面试通知、笔试链接这类邮件通常有「当天内处理」的时效，
 * 一天一收太慢；而每分钟收一次既没必要（也容易被邮箱服务商限流）。
 *
 * 推送口径：**只推本次新到的重要邮件**。全量推送（包括判定为低分的广告）
 * 会让用户很快就把这个组件的通知关掉，等于白做。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailScheduler {

    private final MailService mailService;
    private final NotifyService notifyService;

    @Scheduled(cron = "${nexus.mail.cron:0 */15 * * * *}")
    public void fetch() {
        try {
            MailSettingsDto cfg = mailService.settings();
            if (!cfg.isEnabled() || !cfg.isConfigured()) {
                log.debug("[mail] 未启用或未配置，跳过本次收取");
                return;
            }
            MailSyncResult result = mailService.sync();
            if (!result.isOk() || result.getFetched() == 0) {
                return;
            }
            int important = result.getHigh() + result.getMedium();
            if (important == 0) {
                return;
            }
            StringBuilder body = new StringBuilder();
            for (String line : result.getHighlights()) {
                body.append(line).append("\n");
            }
            if (result.getTodos() > 0) {
                body.append("已自动转 ").append(result.getTodos()).append(" 条待办");
            }
            String title = "📬 收到 " + important + " 封重要邮件";
            notifyService.notifyAsync(NotifyService.EVENT_MAIL, title, body.toString().trim());
            log.info("[mail] 重要邮件推送已触发：{} 封", important);
        } catch (Exception e) {
            // 收信失败已经在 service 里记了 lastError，这里只保证定时线程不炸
            log.error("[mail] 定时收信任务失败", e);
        }
    }
}
