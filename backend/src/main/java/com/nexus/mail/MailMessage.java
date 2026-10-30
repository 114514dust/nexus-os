package com.nexus.mail;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 从邮箱抓下来的一封邮件。
 *
 * 只读副本：本系统从不回写邮箱（不标已读、不移动、不删除），
 * 所以这里的任何字段都可以随便改，不会影响用户在 QQ 邮箱里的真实状态。
 */
@Data
@Entity
@Table(name = "mail_message")
public class MailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 收件邮箱地址。留出多账号的余地：去重键是「账号 + UID」而不是「UID」 */
    @Column(nullable = false, length = 128)
    private String account;

    /** IMAP UID。同一账号内唯一且稳定，增量抓取全靠它判重 */
    @Column(name = "imap_uid", nullable = false)
    private Long imapUid;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "sender_addr", nullable = false, length = 255)
    private String senderAddr;

    @Column(name = "sender_name", length = 128)
    private String senderName;

    @Column(nullable = false, length = 512)
    private String subject;

    /** 正文前 200 字，列表页直接用，不必为了预览去查 TEXT 字段 */
    @Column(length = 1000)
    private String snippet;

    /** 纯文本正文（去 HTML 标签、截断到 2 万字），详情页与 LLM 判定共用 */
    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "important_score", nullable = false)
    private int importantScore;

    /** high / medium / low */
    @Column(name = "important_level", nullable = false, length = 16)
    private String importantLevel = "low";

    /** 打分理由，直接展示给用户看「为什么这封重要」 */
    @Column(name = "score_reason", length = 512)
    private String scoreReason;

    /** 判定来源：rule / llm。用户看到 LLM 参与过会更信任一点 */
    @Column(name = "verdict_by", nullable = false, length = 16)
    private String verdictBy = "rule";

    @Column(name = "has_attachment", nullable = false)
    private boolean hasAttachment;

    /** 抓取那一刻在邮箱里的已读状态（不是本系统的已读） */
    @Column(name = "read_flag", nullable = false)
    private boolean readFlag;

    /** 自动/手动生成的待办 id。待办被删后这里会悬挂，读取时按实际存在性兜底 */
    @Column(name = "todo_id")
    private Long todoId;

    @Column(nullable = false)
    private boolean ignored;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
