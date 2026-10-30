package com.nexus.subscription;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
import com.nexus.message.Message;
import com.nexus.message.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 订阅服务：订阅源 CRUD + 地址自动识别 + 抓取（可插拔 Fetcher）+ 去重 + 入消息流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** space.bilibili.com/3493086340581999 这类空间链接 */
    private static final Pattern BILI_SPACE = Pattern.compile("space\\.bilibili\\.com/(\\d+)");
    /** 纯 uid */
    private static final Pattern BILI_UID = Pattern.compile("^\\d{3,20}$");

    private final SubscriptionRepository subscriptionRepository;
    private final FeedItemRepository feedItemRepository;
    private final MessageRepository messageRepository;
    private final ChangeEventNotifier notifier;
    private final FeedDiscovery feedDiscovery;
    private final BilibiliFetcher bilibiliFetcher;

    @Autowired
    private List<FeedFetcher> fetchers;

    // ---- CRUD ----

    @Transactional
    public Subscription create(SubscriptionCreateRequest req) {
        Subscription sub = new Subscription();
        sub.setType(req.getType());
        sub.setName(req.getName());
        sub.setUrl(req.getUrl());
        sub.setEnabled(true);
        sub.setCreatedAt(LocalDateTime.now());
        Subscription saved = subscriptionRepository.save(sub);
        notifier.notify(ChangeDomain.SUBSCRIPTION);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Subscription> list() {
        return subscriptionRepository.findAll(Sort.by("createdAt").ascending());
    }

    @Transactional
    public void delete(Long id) {
        subscriptionRepository.deleteById(id);
        feedItemRepository.deleteBySubscriptionId(id);
        notifier.notify(ChangeDomain.SUBSCRIPTION);
    }

    @Transactional
    public Subscription toggle(Long id) {
        Subscription sub = getOrThrow(id);
        sub.setEnabled(!sub.isEnabled());
        Subscription saved = subscriptionRepository.save(sub);
        notifier.notify(ChangeDomain.SUBSCRIPTION);
        return saved;
    }

    // ---- 地址识别 ----

    /**
     * 识别用户粘贴的网址：B 站空间链接、RSS 地址、或带订阅声明的博客首页。
     * 识别失败不抛异常，返回 ok=false + 一句人话提示，前端直接展示。
     */
    @Transactional(readOnly = true)
    public SubscriptionDiscoverResult discover(String rawUrl) {
        String input = rawUrl == null ? "" : rawUrl.trim();
        if (input.isEmpty()) {
            return SubscriptionDiscoverResult.fail("先粘贴一个网址");
        }

        // 1. B 站
        String uid = extractBiliUid(input);
        if (uid != null) {
            String name = bilibiliFetcher.fetchAuthorName(uid);
            return build("bilibili", name != null ? name : "B站 UP 主 " + uid, uid, 0,
                    "已识别为 B 站 UP 主");
        }

        // 2. RSS / Atom
        String normalized = FeedDiscovery.normalize(input);
        if (normalized == null) {
            return SubscriptionDiscoverResult.fail("这看起来不是一个网址");
        }
        if (alreadySubscribed(normalized)) {
            return SubscriptionDiscoverResult.fail("这个源已经订阅过了");
        }
        FeedDiscovery.Resolved resolved = feedDiscovery.resolve(normalized);
        if (resolved == null) {
            return SubscriptionDiscoverResult.fail(
                    "没识别出订阅源。可以试试博客的 RSS 地址（常见后缀 /feed、/atom.xml、/rss.xml）");
        }
        if (alreadySubscribed(resolved.getFeedUrl())) {
            return SubscriptionDiscoverResult.fail("这个源已经订阅过了");
        }
        String name = resolved.getTitle();
        if (name == null || name.isEmpty()) {
            name = hostOf(resolved.getFeedUrl());
        }
        String hint = resolved.getFeedUrl().equals(normalized)
                ? "已识别为 RSS"
                : "已识别为 RSS（自动找到了订阅地址）";
        return build("rss", trimName(name), resolved.getFeedUrl(), resolved.getSampleCount(), hint);
    }

    private SubscriptionDiscoverResult build(String type, String name, String url, int sample, String hint) {
        SubscriptionDiscoverResult r = new SubscriptionDiscoverResult();
        r.setOk(true);
        r.setType(type);
        r.setName(name);
        r.setUrl(url);
        r.setSampleCount(sample);
        r.setHint(hint);
        r.setMessage(sample > 0 ? "识别成功，最近 " + sample + " 条内容" : "识别成功");
        return r;
    }

    private boolean alreadySubscribed(String url) {
        for (Subscription s : subscriptionRepository.findAll()) {
            if (s.getUrl() != null && s.getUrl().equalsIgnoreCase(url)) {
                return true;
            }
        }
        return false;
    }

    private static String extractBiliUid(String input) {
        Matcher m = BILI_SPACE.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        if (BILI_UID.matcher(input).matches()) {
            return input;
        }
        return null;
    }

    private static String hostOf(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? url : host;
        } catch (Exception e) {
            return url;
        }
    }

    private static String trimName(String name) {
        String n = name == null ? "" : name.trim();
        return n.length() > 128 ? n.substring(0, 128) : n;
    }

    // ---- 抓取 ----

    /**
     * 手动抓取单个订阅源。
     * 注意：这里故意不加 @Transactional —— 失败时要把 lastError 独立提交，
     * 包在事务里的话「记录错误」会随业务异常一起回滚，卡片上就看不到失败原因了。
     */
    public int fetchOne(Long id) {
        Subscription sub = getOrThrow(id);
        try {
            int added = doFetch(sub);
            // 只在外层通知一次：doFetch 可能被循环调用，逐条推送会造成重复刷新
            if (added > 0) {
                notifier.notify(ChangeDomain.MESSAGE);
            }
            return added;
        } catch (RuntimeException e) {
            // 手动抓取失败时前端有 toast，但也要写到订阅卡片上，不然翻页后就「看不见失败」了
            recordError(sub, e);
            throw e;
        }
    }

    /** 抓取所有启用订阅源（定时任务调用），返回有新增或失败的源明细 */
    @Transactional
    public List<FetchResult> fetchAllEnabled() {
        List<FetchResult> results = new ArrayList<>();
        int total = 0;
        for (Subscription sub : subscriptionRepository.findByEnabledTrue()) {
            try {
                int added = doFetch(sub);
                total += added;
                if (added > 0) {
                    results.add(new FetchResult(sub.getName(), added, null));
                }
            } catch (Exception e) {
                // 单个源失败不拖垮整批：记下来，页面上仍能正常抓其他源
                log.warn("订阅「{}」抓取失败：{}", sub.getName(), e.getMessage());
                recordError(sub, e);
                results.add(new FetchResult(sub.getName(), 0, e.getMessage()));
            }
        }
        if (total > 0) {
            notifier.notify(ChangeDomain.MESSAGE);
        }
        return results;
    }

    /** 把抓取失败写进订阅本身，前端卡片据此显示红字提示 */
    private void recordError(Subscription sub, Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        sub.setLastError(msg.length() > 500 ? msg.substring(0, 500) : msg);
        subscriptionRepository.save(sub);
    }

    /** 单个订阅源的抓取结果，用于汇总推送文案 */
    public static class FetchResult {

        private final String name;
        private final int added;
        private final String error;

        public FetchResult(String name, int added, String error) {
            this.name = name;
            this.added = added;
            this.error = error;
        }

        public String getName() {
            return name;
        }

        public int getAdded() {
            return added;
        }

        public String getError() {
            return error;
        }

        public boolean isFailed() {
            return error != null && !error.isEmpty();
        }
    }

    private int doFetch(Subscription sub) {
        FeedFetcher fetcher = fetchers.stream()
                .filter(f -> f.type().equals(sub.getType()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "不支持的订阅类型: " + sub.getType()));

        List<FetchedItem> items = fetcher.fetch(sub);
        int added = 0;
        for (FetchedItem item : items) {
            if (feedItemRepository.existsBySubscriptionIdAndExternalId(sub.getId(), item.getExternalId())) {
                continue;
            }
            feedItemRepository.save(new FeedItem(sub.getId(), item.getExternalId()));
            messageRepository.save(toMessage(sub, item));
            added++;
        }
        sub.setLastFetchedAt(LocalDateTime.now());
        // 抓成功就把旧错误清掉，卡片上的红字随之消失
        sub.setLastError(null);
        subscriptionRepository.save(sub);
        if (added > 0) {
            log.info("订阅「{}」新增 {} 条消息", sub.getName(), added);
        }
        return added;
    }

    private Message toMessage(Subscription sub, FetchedItem item) {
        Message msg = new Message();
        msg.setSource(sub.getType());
        msg.setSender(sub.getName());
        // 标题与链接拆开存：前端据此把整条做成可点击，而不是让用户复制 URL
        msg.setItemTitle(item.getTitle());
        msg.setItemLink(item.getLink());
        msg.setContent(buildContent(item));
        msg.setReceivedAt(LocalDateTime.now());
        // 发布时间单独存：排序看「什么时候抓到的」，显示看「什么时候发的」
        msg.setPublishedAt(item.getPublishedAt());
        msg.setProcessed(false);
        msg.setMsgRead(false);
        return msg;
    }

    /** 正文 = 标题 + 摘要 + 链接。保留完整文本是为了兼容 AI 工具与每日总结的读取逻辑 */
    private String buildContent(FetchedItem item) {
        StringBuilder sb = new StringBuilder(item.getTitle() == null ? "" : item.getTitle());
        if (item.getContent() != null && !item.getContent().isEmpty()) {
            sb.append('\n').append(item.getContent());
        }
        if (item.getLink() != null && !item.getLink().isEmpty()) {
            sb.append('\n').append(item.getLink());
        }
        return sb.toString();
    }

    private Subscription getOrThrow(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "订阅源不存在"));
    }
}
