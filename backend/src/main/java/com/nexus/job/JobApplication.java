package com.nexus.job;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投递记录（一家公司的一个岗位 = 一条记录）。
 *
 * 为什么把公司和岗位拆成两个字段：同一个公司可能同时投了后端和客户端，
 * 合并成一行会让「进度」互相覆盖，面试时间也会打架。
 */
@Data
@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String company;

    /** 岗位名称，如「Java 后端开发」 */
    @Column(name = "job_title", nullable = false, length = 128)
    private String jobTitle;

    /** 投递类型：秋招 / 春招 / 实习 / 社招 / 国企 / 其他 */
    @Column(nullable = false, length = 24)
    private String category;

    /** 当前进度，见 {@link JobStage} */
    @Column(nullable = false, length = 24)
    private String stage;

    /** 招聘页 / 投递链接 */
    @Column(length = 1024)
    private String url;

    /** 下一步要做什么，如「准备自我介绍」「等 HR 约面」 */
    @Column(name = "next_action", length = 256)
    private String nextAction;

    /** 下一步的日期（面试时间 / 截止时间），只到天 */
    @Column(name = "next_at")
    private LocalDate nextAt;

    /** 重点关注（置顶显示） */
    @Column(nullable = false)
    private boolean starred;

    /** 复盘笔记，支持 Markdown */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
