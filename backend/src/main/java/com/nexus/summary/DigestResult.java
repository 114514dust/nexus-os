package com.nexus.summary;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Agent 服务的返回结果。
 */
@Data
@AllArgsConstructor
public class DigestResult {

    private String summary;
    private String llm;
}
