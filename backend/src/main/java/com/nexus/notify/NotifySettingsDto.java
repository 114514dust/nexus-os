package com.nexus.notify;

import lombok.Data;

/**
 * 消息推送配置。
 *
 * 存在 setting 表的 key-value 里（前缀 notify.），与 LLM 配置同一套存储。
 */
@Data
public class NotifySettingsDto {

    /** 总开关 */
    private boolean enabled;

    /** 渠道标识：feishu / wecom */
    private String channel = "feishu";

    /** 群机器人 Webhook 地址 */
    private String webhookUrl;

    /** 飞书签名校验密钥，留空表示机器人未开启签名 */
    private String secret;

    /** 订阅抓取到新内容时推送 */
    private boolean onFeed;

    /** 每日总结生成后推送 */
    private boolean onDigest;

    /** 求职面试提醒（今日/明日面试、投递长期无进展） */
    private boolean onInterview;

    /** 待办到期提醒（今天到期、已逾期） */
    private boolean onTodo;
}
