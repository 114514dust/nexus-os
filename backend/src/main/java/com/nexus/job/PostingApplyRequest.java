package com.nexus.job;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 「加入求职追踪」的参数，全部可选。
 *
 * 默认是零参数一键加入（岗位名自动从公告里挑一个方向），
 * 提供覆盖字段是为了 AI 对话那种能顺带改一句的场景，比如
 * 「把丝路基金加进来，投的是投资业务岗，标记为重点」。
 */
@Data
public class PostingApplyRequest {

    @Size(max = 128)
    private String jobTitle;

    /** 覆盖投递类型（默认沿用岗位的批次映射） */
    @Size(max = 24)
    private String category;

    private Boolean starred;

    @Size(max = 512)
    private String note;
}
