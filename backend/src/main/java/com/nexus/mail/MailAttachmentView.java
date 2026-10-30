package com.nexus.mail;

import lombok.Data;

/**
 * 附件的前端视图。刻意不暴露本地路径：那是这台机器的目录结构，与用户无关。
 */
@Data
public class MailAttachmentView {

    private Long id;

    private String filename;

    private String contentType;

    /** 服务端不给大小时为 0，前端显示「大小未知」 */
    private long sizeBytes;

    /** 是否已经存到本地（未下载时点下载会现取一次） */
    private boolean downloaded;

    /** 是否可直接在浏览器里看（图片/PDF），前端据此显示「预览」而不是「下载」 */
    private boolean previewable;
}
