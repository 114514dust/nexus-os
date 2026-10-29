package com.nexus.subscription;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 已抓取条目记录，用于去重（同一订阅源同一外部 ID 只入流一次）。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "feed_item")
public class FeedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "external_id", nullable = false, length = 512)
    private String externalId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public FeedItem(Long subscriptionId, String externalId) {
        this.subscriptionId = subscriptionId;
        this.externalId = externalId;
        this.createdAt = LocalDateTime.now();
    }
}
