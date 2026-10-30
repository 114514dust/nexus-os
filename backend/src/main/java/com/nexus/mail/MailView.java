package com.nexus.mail;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件列表/详情的前端视图。
 *
 * 与实体分开的原因：列表页需要几个"算出来的"字段（发件人显示名兜底、
 * 待办是否还存在、待办的到期日），这些不该塞进表结构。
 */
@Data
public class MailView {

    private Long id;

    /** 发件人显示名，没名字时用地址前缀 */
    private String senderName;

    private String senderAddr;

    private String subject;

    private String snippet;

    /** 仅详情接口返回 */
    private String body;

    private LocalDateTime receivedAt;

    /** high / medium / low */
    private String importantLevel;

    private int importantScore;

    private String scoreReason;

    /** rule / llm */
    private String verdictBy;

    private boolean hasAttachment;

    private boolean read;

    private boolean ignored;

    /** 已转成的待办 id（null 表示还没转） */
    private Long todoId;

    /** 待办是否真实存在：用户删掉待办后 todoId 会悬挂，前端据此显示「加入待办」而不是「已加入」 */
    private boolean todoExists;

    /** 待办内容与到期日，列表里直接显示，省得再点进待办组件确认 */
    private String todoContent;

    private String todoDueDate;
}
