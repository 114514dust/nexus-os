package com.nexus.subscription;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.message.Message;
import com.nexus.message.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅服务：订阅源 CRUD + 抓取（可插拔 Fetcher）+ 去重 + 入消息流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final FeedItemRepository feedItemRepository;
    private final MessageRepository messageRepository;

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
        return subscriptionRepository.save(sub);
    }

    @Transactional(readOnly = true)
    public List<Subscription> list() {
        return subscriptionRepository.findAll(Sort.by("createdAt").ascending());
    }

    @Transactional
    public void delete(Long id) {
        subscriptionRepository.deleteById(id);
        feedItemRepository.deleteBySubscriptionId(id);
    }

    @Transactional
    public Subscription toggle(Long id) {
        Subscription sub = getOrThrow(id);
        sub.setEnabled(!sub.isEnabled());
        return subscriptionRepository.save(sub);
    }

    // ---- 抓取 ----

    /** 手动抓取单个订阅源 */
    @Transactional
    public int fetchOne(Long id) {
        return doFetch(getOrThrow(id));
    }

    /** 抓取所有启用订阅源（定时任务调用） */
    @Transactional
    public int fetchAllEnabled() {
        int total = 0;
        for (Subscription sub : subscriptionRepository.findByEnabledTrue()) {
            total += doFetch(sub);
        }
        return total;
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
        msg.setContent(buildContent(item));
        msg.setReceivedAt(LocalDateTime.now());
        msg.setProcessed(false);
        return msg;
    }

    private String buildContent(FetchedItem item) {
        StringBuilder sb = new StringBuilder(item.getTitle());
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
