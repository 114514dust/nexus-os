package com.nexus.subscription;

import java.util.List;

/**
 * 可插拔抓取器接口：每种订阅类型对应一个实现。
 * 新增订阅源（如微信公众号、抖音）只需新增一个实现类，无需改动调度与去重逻辑。
 */
public interface FeedFetcher {

    /** 订阅类型标识，与 subscription.type 对应 */
    String type();

    /** 抓取订阅源的最新条目 */
    List<FetchedItem> fetch(Subscription subscription);
}
