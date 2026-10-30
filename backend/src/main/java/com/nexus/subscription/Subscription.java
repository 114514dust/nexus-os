package com.nexus.subscription;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 订阅源：RSS 地址或 B 站 UP 主 uid。
 */
@Data
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 订阅类型：rss / bilibili */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 128)
    private String name;

    /** rss 类型存 feed 地址；bilibili 类型存 UP 主 uid */
    @Column(nullable = false, length = 512)
    private String url;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    /** 最近一次抓取的错误信息；null 表示上次抓取正常。失败必须可见，不能只写日志 */
    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
