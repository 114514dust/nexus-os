package com.nexus.summary;

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

    private final SummaryService summaryService;

    @Scheduled(cron = "${nexus.agent.digest-cron}")
    public void dailyDigest() {
        try {
            log.info("开始执行每日总结任务");
            Summary summary = summaryService.digest(LocalDate.now());
            log.info("每日总结完成：{} 条消息 / {} 条待办 / {} 条便签，llm={}",
                    summary.getMessageCount(), summary.getTodoCount(),
                    summary.getNoteCount(), summary.getLlmMode());
        } catch (Exception e) {
            log.error("每日总结任务失败", e);
        }
    }
}
