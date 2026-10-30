package com.nexus.job;

import com.nexus.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 校招岗位定时抓取。
 *
 * 时间定在早上 7:30，比待办提醒（8:30）和面试提醒（9:00）都早：
 * 「今天有哪些岗位可以投」是这一天的起点，应该排在最前面看到。
 *
 * 两个约束：
 * 1. 走了 {@code job.autoFetch} 开关 —— 抓取是对外部站点的请求，
 *    用户应该有权关掉它，只留手动刷新；
 * 2. 只推「新入库」且「命中关注词」的岗位。全量推送一天几十条，
 *    等于把提醒变成噪音。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingScheduler {

    /** 推送里最多列几条，多了没人看 */
    private static final int PUSH_LIMIT = 6;

    /** 一次新增超过这个数，基本就是首次全量抓取 */
    private static final int BULK_THRESHOLD = 20;

    private final JobPostingService postingService;
    private final NotifyService notifyService;

    @Scheduled(cron = "${nexus.posting.cron:0 30 7 * * *}")
    public void fetch() {
        try {
            PostingPrefs prefs = postingService.getPrefs();
            if (!prefs.isAutoFetch()) {
                log.debug("[posting] 自动抓取已关闭，跳过");
                return;
            }
            JobPostingService.RefreshResult result = postingService.refresh();
            if (!result.isOk() || result.getAdded() == 0) {
                return;
            }
            // 只取「本次新增」的那些，而不是列表里所有 fresh —— 后者会把历史库存也算进来
            List<Long> newIds = result.getNewIds();
            List<PostingView> added = postingService.list(null, "all", "pending", false).stream()
                    .filter(v -> newIds.contains(v.getId()))
                    .collect(Collectors.toList());
            if (added.isEmpty()) {
                return;
            }

            // 命中关注词的排前面：这才是用户真正关心的「跟我有关的新岗位」
            List<PostingView> hit = added.stream().filter(PostingView::isMatched).collect(Collectors.toList());
            List<PostingView> show = (hit.isEmpty() ? added : hit).stream()
                    .limit(PUSH_LIMIT)
                    .collect(Collectors.toList());

            StringBuilder body = new StringBuilder();
            for (PostingView v : show) {
                body.append("· ").append(v.getCompany());
                if (v.getPositions() != null && !v.getPositions().isBlank()) {
                    body.append(" · ").append(truncate(v.getPositions(), 28));
                }
                if (v.getCity() != null && !v.getCity().isBlank()) {
                    body.append("｜").append(truncate(v.getCity(), 16));
                }
                body.append("\n");
            }
            if (added.size() > show.size()) {
                body.append("…共 ").append(added.size()).append(" 个新岗位\n");
            }

            // 一次进来几十条只可能是首次全量抓取，这时候说「新增」会显得莫名其妙
            boolean bulk = added.size() >= BULK_THRESHOLD;
            String action = bulk ? "📥 已收录 " : "💼 新增 ";
            String title = action + added.size() + " 个校招岗位"
                    + (hit.isEmpty() ? "" : "，" + hit.size() + " 个匹配你的关注词");
            notifyService.notifyAsync(NotifyService.EVENT_POSTING, title, body.toString().trim());
            log.info("[posting] 岗位推送已触发：新增 {}，匹配 {}", added.size(), hit.size());
        } catch (Exception e) {
            log.error("[posting] 定时抓取任务失败", e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
