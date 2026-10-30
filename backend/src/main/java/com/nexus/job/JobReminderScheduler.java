package com.nexus.job;

import com.nexus.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 求职提醒定时任务。
 *
 * 两类提醒，都只在「确实有事要做」时才发：
 * 1. 今天 / 明天有面试或待办 —— 这是求职里唯一不能忘的事；
 * 2. 投了 7 天以上一点动静都没有 —— 提醒用户主动跟进（秋招里很多机会是催出来的）。
 *
 * 与订阅、每日总结一致：推送失败不影响主流程，没配置就静默跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobReminderScheduler {

    /** 超过这个天数没更新，就认为需要跟进 */
    private static final int STALE_DAYS = 7;

    private final JobApplicationRepository repository;
    private final NotifyService notifyService;

    @Scheduled(cron = "${nexus.job.reminder-cron:0 0 9 * * *}")
    public void remind() {
        try {
            LocalDate today = LocalDate.now();
            List<JobApplication> upcoming = repository
                    .findScheduledBetween(today, today.plusDays(1)).stream()
                    .filter(j -> !JobStage.isClosed(j.getStage()))
                    .sorted(Comparator.comparing(JobApplication::getNextAt))
                    .collect(Collectors.toList());

            List<JobApplication> stale = repository
                    .findStaleBefore(LocalDateTime.now().minusDays(STALE_DAYS));

            if (upcoming.isEmpty() && stale.isEmpty()) {
                return;
            }

            StringBuilder body = new StringBuilder();
            for (JobApplication j : upcoming) {
                // 只查了今天和明天，所以用「今天/明天」比重复一遍日期更自然
                String when = j.getNextAt().isEqual(today) ? "今天" : "明天";
                body.append("· ").append(when)
                        .append(" · ").append(JobStage.label(j.getStage()))
                        .append(" · ").append(j.getCompany());
                if (j.getJobTitle() != null && !j.getJobTitle().isEmpty()) {
                    body.append(" · ").append(j.getJobTitle());
                }
                if (j.getNextAction() != null && !j.getNextAction().isEmpty()) {
                    body.append(" —— ").append(j.getNextAction());
                }
                body.append("\n");
            }

            if (!stale.isEmpty()) {
                if (body.length() > 0) {
                    body.append("\n");
                }
                body.append("⏰ ").append(stale.size()).append(" 家投递超过 ")
                        .append(STALE_DAYS).append(" 天没动静，可以主动跟进：");
                String names = stale.stream()
                        .limit(5)
                        .map(j -> j.getCompany() + "（"
                                + ChronoUnit.DAYS.between(j.getUpdatedAt().toLocalDate(), today) + " 天）")
                        .collect(Collectors.joining("、"));
                body.append(names);
                if (stale.size() > 5) {
                    body.append(" 等");
                }
                body.append("\n");
            }

            String title = upcoming.isEmpty()
                    ? "🎯 求职跟进提醒"
                    : "🎯 面试提醒 · " + upcoming.size() + " 项待办";
            notifyService.notifyAsync(NotifyService.EVENT_INTERVIEW, title, body.toString().trim());
            log.info("求职提醒已触发：面试 {} 项 / 待跟进 {} 家", upcoming.size(), stale.size());
        } catch (Exception e) {
            log.error("求职提醒任务失败", e);
        }
    }
}
