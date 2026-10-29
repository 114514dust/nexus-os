package com.nexus.setting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 应用设置服务。当前仅承载 LLM 配置，后续其他设置项沿用同一 key-value 存储。
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String KEY_API_KEY = "llm.apiKey";
    private static final String KEY_BASE_URL = "llm.baseUrl";
    private static final String KEY_MODEL = "llm.model";

    private final SettingRepository settingRepository;

    @Transactional(readOnly = true)
    public LlmSettingsDto get() {
        LlmSettingsDto dto = new LlmSettingsDto();
        dto.setApiKey(getValue(KEY_API_KEY));
        dto.setBaseUrl(getValue(KEY_BASE_URL));
        dto.setModel(getValue(KEY_MODEL));
        return dto;
    }

    @Transactional
    public LlmSettingsDto save(LlmSettingsDto dto) {
        // 仅更新非空字段，避免空值覆盖已有配置
        if (dto.getApiKey() != null) {
            setValue(KEY_API_KEY, dto.getApiKey());
        }
        if (dto.getBaseUrl() != null) {
            setValue(KEY_BASE_URL, dto.getBaseUrl());
        }
        if (dto.getModel() != null) {
            setValue(KEY_MODEL, dto.getModel());
        }
        return get();
    }

    private String getValue(String key) {
        return settingRepository.findByKey(key).map(Setting::getValue).orElse("");
    }

    private void setValue(String key, String value) {
        Setting setting = settingRepository.findByKey(key).orElseGet(Setting::new);
        setting.setKey(key);
        setting.setValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }
}
