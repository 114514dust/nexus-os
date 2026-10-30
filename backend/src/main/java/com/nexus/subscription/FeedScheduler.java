package com.nexus.subscription;

import com.nexus.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订阅抓取定时任务（Spring Task）。
 *
 * 抓取到新内容后主动推送到群机器人——这是「主动推送」的第一个触发源。
 * 手动抓取（页面上点刷新）不推送：用户就在页面上，不需要再打扰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedScheduler {

    private final SubscriptionService subscriptionService;
    private final NotifyService notifyService;

    @Scheduled(cron = "${nexus.feed.cron}")
    public void fetchFeeds() {
        try {
            List<SubscriptionService.FetchResult> results = subscriptionService.fetchAllEnabled();
            if (results.isEmpty()) {
                return;
            }
            int total = results.stream().mapToInt(SubscriptionService.FetchResult::getAdded).sum();

            StringBuilder body = new StringBuilder();
            for (SubscriptionService.FetchResult r : results) {
                if (r.isFailed()) {
                    body.append("· ").append(r.getName()).append("：抓取失败\n");
                } else {
                    body.append("· ").append(r.getName()).append("：").append(r.getAdded()).append(" 条\n");
                }
            }
            if (total > 0) {
                log.info("订阅抓取完成，新增 {} 条消息", total);
                notifyService.notifyAsync(
                        NotifyService.EVENT_FEED,
                        "📡 订阅更新 · " + total + " 条新内容",
                        body.toString().trim());
            } else {
                // 没有新内容也全是失败时，值得告诉用户「不是没更新，是抓不到」
                log.warn("订阅抓取全部失败或没有新内容：{}", body.toString().trim());
            }
        } catch (Exception e) {
            log.error("订阅抓取任务失败", e);
        }
    }
}
