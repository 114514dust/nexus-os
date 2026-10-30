package com.nexus.mail;

import com.nexus.setting.Setting;
import com.nexus.setting.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 邮箱配置读写。
 *
 * 单独成类而不是塞进 SettingsService：邮件配置有十几个字段，还有「授权码打码回显」
 * 这种只属于它的规则，混进通用设置服务会让那个类变成杂物间。
 *
 * 一条安全约定：**明文授权码只在本包内流转**。对外接口（{@link #view()}）永远返回打码值，
 * 保存时以 **** 开头的值直接忽略 —— 前端拿打码值原样提交回来是常规操作，
 * 不能因此把真授权码覆盖成星号。
 */
@Service
@RequiredArgsConstructor
public class MailSettingsService {

    private static final String KEY_ENABLED = "mail.enabled";
    private static final String KEY_ADDRESS = "mail.address";
    private static final String KEY_AUTH_CODE = "mail.authCode";
    private static final String KEY_HOST = "mail.imapHost";
    private static final String KEY_PORT = "mail.imapPort";
    private static final String KEY_SSL = "mail.ssl";
    private static final String KEY_FOLDER = "mail.folder";
    private static final String KEY_AUTO_TODO = "mail.autoTodo";
    private static final String KEY_LLM_JUDGE = "mail.llmJudge";
    private static final String KEY_IGNORE_SENDERS = "mail.ignoreSenders";
    private static final String KEY_EXTRA_KEYWORDS = "mail.extraKeywords";
    private static final String KEY_FETCH_LIMIT = "mail.fetchLimit";
    private static final String KEY_LAST_SYNC = "mail.lastSyncAt";
    private static final String KEY_LAST_ERROR = "mail.lastError";

    private static final String MASK_PREFIX = "****";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SettingRepository settingRepository;

    /** 明文配置，仅供本包连接邮箱使用。不要把它直接返回给 HTTP 层。 */
    @Transactional(readOnly = true)
    public MailSettingsDto load() {
        MailSettingsDto dto = new MailSettingsDto();
        dto.setEnabled(Boolean.parseBoolean(getValue(KEY_ENABLED)));
        dto.setAddress(getValue(KEY_ADDRESS));
        dto.setAuthCode(getValue(KEY_AUTH_CODE));
        dto.setImapHost(orDefault(getValue(KEY_HOST), "imap.qq.com"));
        dto.setImapPort(parseInt(getValue(KEY_PORT), 993));
        dto.setFolder(orDefault(getValue(KEY_FOLDER), "INBOX"));
        dto.setSsl(parseBool(getValue(KEY_SSL), true));
        // 两个开关默认开：宁可多建几条待办（能删），也别漏掉面试通知（补不回来）
        dto.setAutoTodo(parseBool(getValue(KEY_AUTO_TODO), true));
        dto.setLlmJudge(parseBool(getValue(KEY_LLM_JUDGE), true));
        dto.setIgnoreSenders(getValue(KEY_IGNORE_SENDERS));
        dto.setExtraKeywords(getValue(KEY_EXTRA_KEYWORDS));
        dto.setFetchLimit(clamp(parseInt(getValue(KEY_FETCH_LIMIT), 30), 5, 200));
        dto.setLastSyncAt(getValue(KEY_LAST_SYNC));
        dto.setLastError(getValue(KEY_LAST_ERROR));
        dto.setConfigured(!isBlank(dto.getAddress()) && !isBlank(dto.getAuthCode()));
        return dto;
    }

    /** 对前端展示的版本：授权码打码 */
    @Transactional(readOnly = true)
    public MailSettingsDto view() {
        MailSettingsDto dto = load();
        dto.setAuthCode(mask(dto.getAuthCode()));
        // 没配置账号时，上一次的时间与错误都没有意义，别在界面上留着误导人
        if (!dto.isConfigured()) {
            dto.setLastSyncAt("");
            dto.setLastError("");
        }
        return dto;
    }

    @Transactional
    public MailSettingsDto save(MailSettingsDto dto) {
        setValue(KEY_ENABLED, String.valueOf(dto.isEnabled()));
        if (dto.getAddress() != null) {
            setValue(KEY_ADDRESS, dto.getAddress().trim());
        }
        // 打码值代表「没动过授权码」，不能拿它覆盖真实值
        if (dto.getAuthCode() != null && !dto.getAuthCode().startsWith(MASK_PREFIX)) {
            setValue(KEY_AUTH_CODE, dto.getAuthCode().trim());
        }
        if (dto.getImapHost() != null && !dto.getImapHost().isBlank()) {
            setValue(KEY_HOST, dto.getImapHost().trim());
        }
        if (dto.getImapPort() > 0) {
            setValue(KEY_PORT, String.valueOf(dto.getImapPort()));
        }
        setValue(KEY_SSL, String.valueOf(dto.isSsl()));
        if (dto.getFolder() != null && !dto.getFolder().isBlank()) {
            setValue(KEY_FOLDER, dto.getFolder().trim());
        }
        if (dto.getIgnoreSenders() != null) {
            setValue(KEY_IGNORE_SENDERS, dto.getIgnoreSenders().trim());
        }
        if (dto.getExtraKeywords() != null) {
            setValue(KEY_EXTRA_KEYWORDS, dto.getExtraKeywords().trim());
        }
        if (dto.getFetchLimit() > 0) {
            setValue(KEY_FETCH_LIMIT, String.valueOf(clamp(dto.getFetchLimit(), 5, 200)));
        }
        // 开关一律覆盖：前端表单总是带全量布尔值
        setValue(KEY_AUTO_TODO, String.valueOf(dto.isAutoTodo()));
        setValue(KEY_LLM_JUDGE, String.valueOf(dto.isLlmJudge()));
        // 改了配置，上一次的失败原因就不再适用，留着只会误导用户
        setValue(KEY_LAST_ERROR, "");
        return view();
    }

    @Transactional
    public void markSynced(LocalDateTime at) {
        setValue(KEY_LAST_SYNC, at == null ? "" : at.format(TS));
        setValue(KEY_LAST_ERROR, "");
    }

    /**
     * 记录失败原因。只留最后一条：用户关心的是「现在为什么收不到」，
     * 不是失败历史；历史在日志里。
     */
    @Transactional
    public void markFailed(String reason) {
        String text = reason == null ? "" : reason.replaceAll("\\s+", " ").trim();
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }
        setValue(KEY_LAST_ERROR, text);
    }

    private static String mask(String code) {
        if (isBlank(code)) {
            return "";
        }
        String tail = code.length() <= 4 ? code : code.substring(code.length() - 4);
        return MASK_PREFIX + tail;
    }

    private String getValue(String key) {
        return settingRepository.findByKey(key).map(Setting::getValue).orElse("");
    }

    private void setValue(String key, String value) {
        Setting setting = settingRepository.findByKey(key).orElseGet(Setting::new);
        setting.setKey(key);
        setting.setValue(value == null ? "" : value);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String orDefault(String s, String def) {
        return isBlank(s) ? def : s.trim();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean parseBool(String s, boolean def) {
        if (s == null || s.isEmpty()) {
            return def;
        }
        return Boolean.parseBoolean(s);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
