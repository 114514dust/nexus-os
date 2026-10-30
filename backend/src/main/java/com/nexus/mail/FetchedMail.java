package com.nexus.mail;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 刚从 IMAP 抓下来、还没入库的一封邮件。
 *
 * 与 {@link MailMessage} 分开：这个对象只承载「邮箱里读到的原始信息」，
 * 没有打分、没有待办关联 —— 那些是抓取之后的判定链路才产生的。
 */
@Data
public class FetchedMail {

    private Long imapUid;

    private String messageId;

    private String senderAddr;

    private String senderName;

    private String subject;

    private String snippet;

    private String body;

    private LocalDateTime sentAt;

    private LocalDateTime receivedAt;

    private boolean hasAttachment;

    /** 抓取时在邮箱里是否已读 */
    private boolean read;
}
