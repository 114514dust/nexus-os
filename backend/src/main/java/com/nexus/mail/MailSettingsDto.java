package com.nexus.mail;

import lombok.Data;

/**
 * 邮箱订阅配置。存在 setting 表的 key-value 里（前缀 mail.），与 LLM / 推送配置同一套存储。
 */
@Data
public class MailSettingsDto {

    /** 定时自动收取开关。关掉后仍可手动点「收取」 */
    private boolean enabled;

    /** 收件邮箱地址，如 xxx@qq.com */
    private String address;

    /**
     * IMAP 授权码（不是 QQ 密码）。
     * 查询时打码回显（****abcd），保存时以 **** 开头的值视为「没改」。
     */
    private String authCode;

    private String imapHost = "imap.qq.com";

    private int imapPort = 993;

    /** 是否用 SSL/TLS 直连。QQ 邮箱 993 必须开；自建/内网 IMAP 常见 143 明文 */
    private boolean ssl = true;

    /** 收信文件夹，QQ 邮箱默认 INBOX */
    private String folder = "INBOX";

    /** 高置信度邮件自动转待办 */
    private boolean autoTodo = true;

    /** 灰区邮件交给 LLM 精判（无 LLM 配置时自动降级为纯规则） */
    private boolean llmJudge = true;

    /** 永不重要的发件人，逗号分隔（支持域名，如 @mail.qq.com） */
    private String ignoreSenders;

    /** 额外关注关键词，逗号分隔。命中直接抬成重要 */
    private String extraKeywords;

    /** 单次最多读取最近多少封，避免首次同步把几年邮件全拉下来 */
    private int fetchLimit = 30;

    /** 每天早上推送一封「昨天的要紧邮件」摘要 */
    private boolean digestEnabled = true;

    // ---- 只读回显 ----

    /** 上次收取完成时间（yyyy-MM-dd HH:mm），未收取过为空 */
    private String lastSyncAt;

    /** 上次推送摘要的日期（yyyy-MM-dd）。同一天不重复推，重启也不会补发 */
    private String lastDigestDate;

    /** 地址与授权码都填了才允许连接 */
    private boolean configured;

    /** 上次收取失败的原因，成功后清空。前端直接显示，省得用户猜 */
    private String lastError;
}
