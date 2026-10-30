package com.nexus.setting;

import com.nexus.notify.NotifySettingsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 应用设置服务。承载 LLM 配置与消息推送配置，均沿用同一 key-value 存储。
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String KEY_API_KEY = "llm.apiKey";
    private static final String KEY_BASE_URL = "llm.baseUrl";
    private static final String KEY_MODEL = "llm.model";

    private static final String KEY_NOTIFY_ENABLED = "notify.enabled";
    private static final String KEY_NOTIFY_CHANNEL = "notify.channel";
    private static final String KEY_NOTIFY_WEBHOOK = "notify.webhookUrl";
    private static final String KEY_NOTIFY_SECRET = "notify.secret";
    private static final String KEY_NOTIFY_ON_FEED = "notify.onFeed";
    private static final String KEY_NOTIFY_ON_DIGEST = "notify.onDigest";
    private static final String KEY_NOTIFY_ON_INTERVIEW = "notify.onInterview";
    private static final String KEY_NOTIFY_ON_TODO = "notify.onTodo";
    private static final String KEY_NOTIFY_ON_POSTING = "notify.onPosting";
    private static final String KEY_NOTIFY_ON_MAIL = "notify.onMail";
    private static final String KEY_NOTIFY_ON_MAIL_DIGEST = "notify.onMailDigest";

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

    // ---- 消息推送配置 ----

    @Transactional(readOnly = true)
    public NotifySettingsDto getNotify() {
        NotifySettingsDto dto = new NotifySettingsDto();
        dto.setEnabled(Boolean.parseBoolean(getValue(KEY_NOTIFY_ENABLED)));
        String channel = getValue(KEY_NOTIFY_CHANNEL);
        dto.setChannel(channel.isEmpty() ? "feishu" : channel);
        dto.setWebhookUrl(getValue(KEY_NOTIFY_WEBHOOK));
        dto.setSecret(getValue(KEY_NOTIFY_SECRET));
        dto.setOnFeed(Boolean.parseBoolean(getValue(KEY_NOTIFY_ON_FEED)));
        dto.setOnDigest(Boolean.parseBoolean(getValue(KEY_NOTIFY_ON_DIGEST)));
        dto.setOnInterview(Boolean.parseBoolean(getValue(KEY_NOTIFY_ON_INTERVIEW)));
        dto.setOnTodo(Boolean.parseBoolean(getValue(KEY_NOTIFY_ON_TODO)));
        dto.setOnPosting(Boolean.parseBoolean(getValue(KEY_NOTIFY_ON_POSTING)));
        dto.setOnMail(Boolean.parseBoolean(getValue(KEY_NOTIFY_ON_MAIL)));
        dto.setOnMailDigest(Boolean.parseBoolean(getValue(KEY_NOTIFY_ON_MAIL_DIGEST)));
        return dto;
    }

    @Transactional
    public NotifySettingsDto saveNotify(NotifySettingsDto dto) {
        // 开关类字段前端总会带上，直接覆盖；文本类只在非 null 时更新，避免空值抹掉已有配置
        setValue(KEY_NOTIFY_ENABLED, String.valueOf(dto.isEnabled()));
        if (dto.getChannel() != null) {
            setValue(KEY_NOTIFY_CHANNEL, dto.getChannel());
        }
        if (dto.getWebhookUrl() != null) {
            setValue(KEY_NOTIFY_WEBHOOK, dto.getWebhookUrl().trim());
        }
        if (dto.getSecret() != null) {
            setValue(KEY_NOTIFY_SECRET, dto.getSecret().trim());
        }
        setValue(KEY_NOTIFY_ON_FEED, String.valueOf(dto.isOnFeed()));
        setValue(KEY_NOTIFY_ON_DIGEST, String.valueOf(dto.isOnDigest()));
        setValue(KEY_NOTIFY_ON_INTERVIEW, String.valueOf(dto.isOnInterview()));
        setValue(KEY_NOTIFY_ON_TODO, String.valueOf(dto.isOnTodo()));
        setValue(KEY_NOTIFY_ON_POSTING, String.valueOf(dto.isOnPosting()));
        setValue(KEY_NOTIFY_ON_MAIL, String.valueOf(dto.isOnMail()));
        setValue(KEY_NOTIFY_ON_MAIL_DIGEST, String.valueOf(dto.isOnMailDigest()));
        return getNotify();
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
