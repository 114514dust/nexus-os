package com.nexus.job;

import lombok.Data;

import java.util.List;

/**
 * 岗位抓取的偏好设置。
 *
 * focusKeywords 是「我关心什么岗位」，命中就打标并置顶：
 * 抓来的岗位一天几十条，不给个「跟你有关」的信号，这个组件很快就变成没人看的列表。
 */
@Data
public class PostingPrefs {

    private List<String> focusKeywords;

    /** 是否允许定时抓取（关掉后只能手动刷新） */
    private boolean autoFetch;

    /** 每次抓取的页数（1 页 ≈ 30 条） */
    private int pages;
}
