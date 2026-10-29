package com.nexus.algorithm;

import lombok.Data;

/**
 * 更新算法题请求，字段为 null 表示不更新。
 * star 取值范围 0~5，note 可为空串表示清空笔记。
 */
@Data
public class AlgorithmUpdateRequest {

    private String title;
    private String difficulty;
    private String leetcodeUrl;
    private Integer frequency;
    private Integer star;
    private String note;
}
