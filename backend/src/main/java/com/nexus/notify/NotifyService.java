package com.nexus.notify;

import com.nexus.setting.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 消息推送服务：把系统里发生的事主动推到群机器人。
 *
 * 三条硬约束（决定了实现方式）：
 * 1. **推送失败绝不能影响主流程**。抓取、每日总结是主业务，推送只是附属动作，
 *    所以异步执行 + 吞掉异常，只记日志，绝不能让推送超时拖垮定时任务。
 * 2. **同类事件去重**。定时任务重试或短时间多源同时更新时，不能刷屏，
 *    用内存时间窗限制同一 event 在 60 秒内只推一次。
 * 3. **配置不全就静默跳过**。没配开关或地址时直接 return，不抛异常打断业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    /** 订阅有新内容 */
    public static final String EVENT_FEED = "feed";

    /** 每日总结生成完毕 */
    public static final String EVENT_DIGEST = "digest";

    /** 求职面试提醒 */
    public static final String EVENT_INTERVIEW = "interview";

    /** 同类型事件的去重窗口（毫秒） */
    private static final long DEDUP_WINDOW_MS = 60_000L;

    /** 推送线程池：守护线程，不阻塞应用关闭；并发 2 足够个人场景 */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "notify-send");
        t.setDaemon(true);
        return t;
    });

    private final List<NotifyChannel> channels;
    private final SettingsService settingsService;

    /** event -> 上次推送时间戳，用于去重 */
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    /**
     * 异步推送。业务侧只管调用，任何失败都不会向上冒泡。
     */
    public void notifyAsync(String event, String title, String content) {
        NotifySettingsDto cfg;
        try {
            cfg = settingsService.getNotify();
        } catch (Exception e) {
            log.warn("读取推送配置失败，跳过本次推送", e);
            return;
        }
        if (!enabledFor(cfg, event) || isDuplicate(event)) {
            return;
        }
        POOL.submit(() -> {
            try {
                doSend(cfg, title, content);
                log.info("推送成功：event={} channel={}", event, cfg.getChannel());
            } catch (Exception e) {
                log.warn("推送失败：event={} channel={} 原因={}", event, cfg.getChannel(), e.getMessage());
            }
        });
    }

    /**
     * 同步发送测试消息。配置错了必须让用户立刻看到原因，所以这里不吞异常。
     */
    public void sendTest() throws Exception {
        NotifySettingsDto cfg = settingsService.getNotify();
        doSend(cfg, "Nexus 测试消息", "看到这条说明推送配置已生效 ✅");
    }

    private void doSend(NotifySettingsDto cfg, String title, String content) throws Exception {
        String url = cfg.getWebhookUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException("尚未配置 Webhook 地址");
        }
        String channelType = cfg.getChannel() == null ? "feishu" : cfg.getChannel();
        NotifyChannel channel = channels.stream()
                .filter(c -> c.type().equals(channelType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("不支持的推送渠道：" + channelType));
        // 文本消息没有标题字段，把标题作为正文首行
        channel.send(title + "\n" + content, cfg);
    }

    private boolean enabledFor(NotifySettingsDto cfg, String event) {
        if (!cfg.isEnabled()) {
            return false;
        }
        if (EVENT_FEED.equals(event)) {
            return cfg.isOnFeed();
        }
        if (EVENT_DIGEST.equals(event)) {
            return cfg.isOnDigest();
        }
        if (EVENT_INTERVIEW.equals(event)) {
            return cfg.isOnInterview();
        }
        return false;
    }

    private boolean isDuplicate(String event) {
        long now = System.currentTimeMillis();
        Long prev = lastSentAt.get(event);
        if (prev != null && now - prev < DEDUP_WINDOW_MS) {
            log.debug("推送去重：event={} 距上次 {}ms", event, now - prev);
            return true;
        }
        lastSentAt.put(event, now);
        return false;
    }
}
