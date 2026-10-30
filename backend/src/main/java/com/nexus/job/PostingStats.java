package com.nexus.job;

import lombok.Data;

import java.time.LocalDateTime;

/** 岗位池概览，口径同求职统计：基于全量，不受列表筛选影响 */
@Data
public class PostingStats {

    /** 入库总数（不含已忽略） */
    private long total;
    /** 待处理：未加入追踪、未忽略 */
    private long pending;
    /** 已加入求职追踪 */
    private long applied;
    /** 近 3 天新入库 */
    private long fresh;
    /** 收藏 */
    private long starred;
    /** 主来源名称，用于界面注明数据出处 */
    private String sourceLabel;
    private LocalDateTime lastFetchedAt;
}
