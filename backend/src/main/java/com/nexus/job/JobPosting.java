package com.nexus.job;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 校招岗位（外部渠道抓来的「在招职位」），与 {@link JobApplication} 互补：
 * 前者是「外面有什么」，后者是「我投了什么」。
 *
 * 抓取源把一家公司的招聘公告作为一条记录（公告里通常含多个岗位方向），
 * 所以 title 是公告标题、positions 是方向明细，两者分开存。
 */
@Data
@Entity
@Table(name = "job_posting",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_posting_source",
                columnNames = {"source", "source_id"}))
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 来源标识，目前是 qiuzhaowang，manual 表示手动添加 */
    @Column(nullable = false, length = 32)
    private String source;

    /** 来源站点内的唯一 id，用于去重 */
    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @Column(nullable = false, length = 128)
    private String company;

    /** 公告标题，如「丝路基金2027届校园招聘」 */
    @Column(nullable = false, length = 256)
    private String title;

    /** 岗位方向明细，如「投资业务岗 · 非投资业务岗」 */
    @Column(length = 2048)
    private String positions;

    @Column(length = 256)
    private String city;

    /** 届别，如 2027届 */
    @Column(length = 32)
    private String cohort;

    /** 批次，如 27秋招 / 秋招正式批 / 春招专场 */
    @Column(length = 32)
    private String batch;

    /** 企业性质：国企 / 民企 / 事业单位 / 中外合资 */
    @Column(name = "company_type", length = 32)
    private String companyType;

    /** 映射到 {@link JobCategory}，用于「加入追踪」时填投递类型 */
    @Column(nullable = false, length = 24)
    private String category = JobCategory.OTHER;

    /** 来源站点的详情页 */
    @Column(name = "detail_url", length = 512)
    private String detailUrl;

    /** 招聘方官方公告（真正靠谱的投递入口） */
    @Column(name = "announce_url", length = 1024)
    private String announceUrl;

    /** 截止文案，站点给的可能是「尽快投递」「2026-11-15」这类自由文本 */
    @Column(name = "deadline_text", length = 64)
    private String deadlineText;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(nullable = false)
    private boolean starred;

    /** 忽略：不想再看到这条（不回删数据，避免下次抓取又冒出来） */
    @Column(nullable = false)
    private boolean ignored;

    /** 加入求职追踪后回填的投递 id，空表示还没处理 */
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
