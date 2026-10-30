package com.nexus.todo;

import com.nexus.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 待办到期提醒。
 *
 * 只提醒「今天到期」和「已经逾期」的未完成待办，理由：
 * 1. **不做提前提醒**。个人待办提前一天提示，多数时候只会让人把消息划掉，
 *    真正的价值是「今天该做完的别忘」和「欠着的别装看不见」；
 * 2. **没填日期的待办不参与**。随手记的事本来就没有期限，提醒它等于制造噪音；
 * 3. 与其它推送一致：异步、失败吞掉、没配置就静默跳过，永远不拖累主流程。
 *
 * 时间上比求职提醒（9:00）早半小时，让「今天的待办」排在「今天的面试」前面。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoReminderScheduler {

    /** 每条清单最多列几项，超出用「等 N 项」概括，避免推送变成一堵墙 */
    private static final int MAX_ITEMS = 6;

    /** 单条待办在推送里的最大显示长度 */
    private static final int MAX_LABEL = 24;

    private final TodoRepository todoRepository;
    private final NotifyService notifyService;

    @Scheduled(cron = "${nexus.todo.reminder-cron:0 30 8 * * *}")
    public void remind() {
        try {
            LocalDate today = LocalDate.now();
            List<Todo> pending = todoRepository
                    .findByDoneFalseAndDueDateLessThanEqualOrderByDueDateAsc(today);
            if (pending.isEmpty()) {
                return;
            }

            List<Todo> overdue = pending.stream()
                    .filter(t -> t.getDueDate().isBefore(today))
                    .collect(Collectors.toList());
            List<Todo> dueToday = pending.stream()
                    .filter(t -> today.equals(t.getDueDate()))
                    .collect(Collectors.toList());

            StringBuilder body = new StringBuilder();
            if (!overdue.isEmpty()) {
                body.append("· 已逾期（").append(overdue.size()).append("）：")
                        .append(overdue.stream().limit(MAX_ITEMS)
                                .map(t -> brief(t.getContent()) + "（"
                                        + ChronoUnit.DAYS.between(t.getDueDate(), today) + " 天）")
                                .collect(Collectors.joining("、")))
                        .append(overdue.size() > MAX_ITEMS ? " 等" : "")
                        .append("\n");
            }
            if (!dueToday.isEmpty()) {
                body.append("· 今天到期（").append(dueToday.size()).append("）：")
                        .append(dueToday.stream().limit(MAX_ITEMS)
                                .map(t -> brief(t.getContent()))
                                .collect(Collectors.joining("、")))
                        .append(dueToday.size() > MAX_ITEMS ? " 等" : "")
                        .append("\n");
            }

            String title = "📋 待办提醒 · " + pending.size() + " 项待处理";
            notifyService.notifyAsync(NotifyService.EVENT_TODO, title, body.toString().trim());
            log.info("待办提醒已触发：今天 {} 项 / 逾期 {} 项", dueToday.size(), overdue.size());
        } catch (Exception e) {
            log.error("待办提醒任务失败", e);
        }
    }

    /** 待办正文最长 512 字，直接塞进推送会撑爆消息，截断留省略号 */
    private static String brief(String content) {
        if (content == null) {
            return "";
        }
        String c = content.trim().replaceAll("\\s+", " ");
        return c.length() <= MAX_LABEL ? c : c.substring(0, MAX_LABEL) + "…";
    }
}
