package com.nexus.mail;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /** 附件元信息（不含字节，下载时再回 IMAP 取） */
    private List<AttachmentMeta> attachments = new ArrayList<>();

    @Data
    public static class AttachmentMeta {

        /** MIME 树里第几个附件，从 0 开始。下载时按同一顺序取回 */
        private int partIndex;

        private String filename;

        private String contentType;

        /** 服务端不给大小时为 0 */
        private long size;
    }
}
