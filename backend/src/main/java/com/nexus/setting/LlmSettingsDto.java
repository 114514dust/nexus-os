package com.nexus.setting;

import lombok.Data;

/**
 * LLM 配置（供前端设置页编辑，后端透传给 Python Agent 覆盖环境变量）。
 */
@Data
public class LlmSettingsDto {

    private String apiKey;

    private String baseUrl;

    private String model;
}
