package com.nexus.subscription;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 订阅源仓储。
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByEnabledTrue();

    List<Subscription> findAll(Sort sort);
}
