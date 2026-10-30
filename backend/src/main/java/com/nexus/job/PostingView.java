package com.nexus.job;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位列表项。直接面向组件渲染，所以把「是不是新岗位」「有没有命中关注词」
 * 这些派生信息在服务端算好 —— 前端不该为了一条卡片再跑一遍业务规则。
 */
@Data
public class PostingView {

    private Long id;
    private String source;
    private String company;
    private String title;
    private String positions;
    private String city;
    private String cohort;
    private String batch;
    private String companyType;
    private String category;
    private String categoryLabel;
    private String detailUrl;
    private String announceUrl;
    private String deadlineText;
    private LocalDate publishedAt;

    private boolean starred;
    private boolean ignored;

    /** 已加入求职追踪时非空 */
    private Long jobId;

    /** 近 3 天入库且还没处理 —— 列表里打 NEW 标 */
    private boolean fresh;

    /** 命中关注关键词 */
    private boolean matched;
    private List<String> matchKeywords;

    private LocalDateTime fetchedAt;
}
