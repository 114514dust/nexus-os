package com.nexus.mail;

import lombok.Data;

/**
 * 邮箱概览统计。与岗位组件的 PostingStats 保持同一套「让用户一眼看到今天有什么」的思路。
 */
@Data
public class MailStats {

    /** 未忽略的邮件总数（只统计已入库的） */
    private long total;

    /** 高置信度 */
    private long high;

    /** 中置信度 */
    private long medium;

    /** 已转成待办的邮件数 */
    private long toTodo;

    private String lastSyncAt;

    private String lastError;

    private boolean configured;
}
