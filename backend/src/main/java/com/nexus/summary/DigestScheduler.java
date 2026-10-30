package com.nexus.summary;

import com.nexus.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日总结定时任务（Spring Task）。
 *
 * 选型说明：个人工作台是单机场景，Spring Task（@Scheduled）足够；
 * 若将来多实例部署，可加 ShedLock 防重复执行，或升级 Quartz/XXL-JOB。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DigestScheduler {

    /** 推送正文的长度上限，避免超长内容被渠道拒收 */
    private static final int MAX_PUSH_CHARS = 1500;

    private final SummaryService summaryService;
    private final NotifyService notifyService;

    @Scheduled(cron = "${nexus.agent.digest-cron}")
    public void dailyDigest() {
        try {
            log.info("开始执行每日总结任务");
            Summary summary = summaryService.digest(LocalDate.now());
            log.info("每日总结完成：{} 条消息 / {} 条待办 / {} 条便签，llm={}",
                    summary.getMessageCount(), summary.getTodoCount(),
                    summary.getNoteCount(), summary.getLlmMode());

            String body = String.format("今日 %d 条消息 / %d 条待办 / %d 条便签\n\n%s",
                    summary.getMessageCount(), summary.getTodoCount(), summary.getNoteCount(),
                    truncate(summary.getContent(), MAX_PUSH_CHARS));
            notifyService.notifyAsync(
                    NotifyService.EVENT_DIGEST,
                    "🌙 " + summary.getSummaryDate() + " 每日总结",
                    body);
        } catch (Exception e) {
            log.error("每日总结任务失败", e);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
