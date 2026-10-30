package com.nexus.mail;

import lombok.Data;

/**
 * 一次收信的结果，直接回给前端与定时任务。
 */
@Data
public class MailSyncResult {

    private boolean ok;

    /** 失败原因 / 成功时的补充说明，前端原样显示 */
    private String message;

    /** 邮箱里本次读到的新邮件数 */
    private int fetched;

    /** 判为高置信度的数量 */
    private int high;

    /** 判为中置信度的数量 */
    private int medium;

    /** 本次自动创建的待办数 */
    private int todos;

    /** 本次判为重要的邮件摘要（最多 8 条），推送文案直接用 */
    private java.util.List<String> highlights = new java.util.ArrayList<>();

    public static MailSyncResult fail(String message) {
        MailSyncResult r = new MailSyncResult();
        r.setOk(false);
        r.setMessage(message);
        return r;
    }
}
