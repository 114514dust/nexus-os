package com.nexus.algorithm;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 创建算法题请求。
 */
@Data
public class AlgorithmCreateRequest {

    @NotBlank
    private String title;

    /** 难度：easy / medium / hard，默认 medium */
    private String difficulty = "medium";

    @NotBlank
    private String leetcodeUrl;

    /** 面试出现频率（热度），默认 0 */
    private int frequency = 0;
}
