package com.nexus.job;

import lombok.Data;

/** 岗位的两个用户状态：收藏、忽略。都为 null 表示什么都不改。 */
@Data
public class PostingUpdateRequest {

    private Boolean starred;

    private Boolean ignored;
}
