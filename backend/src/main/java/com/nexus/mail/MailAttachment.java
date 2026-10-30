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
 * 一封邮件的附件（元信息）。
 *
 * 字节不在收信时落盘：订阅的邮箱里，营销彩页、几十兆的宣讲手册比比皆是，
 * 无脑保存只会把磁盘填满，而真正会被打开的（面试须知、笔试题）不到一成。
 * 所以这里只记「有什么附件」，等用户点下载时再回 IMAP 取一次。
 *
 * partIndex 是 MIME 树里第几个附件（从 0 开始）。同一封邮件的 MIME 结构不会变，
 * 所以下载时用同一套遍历顺序能稳定取回同一个附件 —— 这也是不存「IMAP section」
 * 的原因：section 编号（如 1.2）解析起来容易出错，序号更直观。
 */
@Data
@Entity
@Table(name = "mail_attachment")
public class MailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mail_id", nullable = false)
    private Long mailId;

    @Column(name = "part_index", nullable = false)
    private int partIndex;

    /** 原始文件名（已解码 encoded-word），下载时用它做 Content-Disposition */
    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    /** JavaMail 给的是估算值，服务端不给时是 0 */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** 本地绝对路径，为空表示还没下载过 */
    @Column(name = "stored_path", length = 512)
    private String storedPath;

    @Column(name = "downloaded_at")
    private LocalDateTime downloadedAt;
}
