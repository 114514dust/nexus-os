package com.nexus.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订阅抓取定时任务（Spring Task）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${nexus.feed.cron}")
    public void fetchFeeds() {
        try {
            int added = subscriptionService.fetchAllEnabled();
            if (added > 0) {
                log.info("订阅抓取完成，新增 {} 条消息", added);
            }
        } catch (Exception e) {
            log.error("订阅抓取任务失败", e);
        }
    }
}
