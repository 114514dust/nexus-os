package com.nexus.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 已抓取条目仓储（去重）。
 */
public interface FeedItemRepository extends JpaRepository<FeedItem, Long> {

    boolean existsBySubscriptionIdAndExternalId(Long subscriptionId, String externalId);

    void deleteBySubscriptionId(Long subscriptionId);
}
