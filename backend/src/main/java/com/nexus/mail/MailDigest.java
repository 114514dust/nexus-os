package com.nexus.mail;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一天的邮件摘要。
 *
 * 只汇总「要紧的」（高/中置信度）和「今天到期的事」，
 * 普通邮件只给个数字 —— 摘要是用来三十秒看完的，不是邮箱的镜像。
 */
@Data
public class MailDigest {

    /** yyyy-MM-dd */
    private String date;

    /** 当天收到的邮件总数（含被判为普通的） */
    private int total;

    private int high;

    private int medium;

    private int low;

    /** 当天收到的邮件里已经转成待办的条数 */
    private int todos;

    /** 高/中置信度的邮件，按时间正序 */
    private List<Item> items = new ArrayList<>();

    /** 今天到期、且由邮件生成的待办（可能是前几天收到的邮件） */
    private List<String> dueToday = new ArrayList<>();

    /** 发件人分布，如「学校就业中心 ×4」，只列出现两次以上的 */
    private List<String> topSenders = new ArrayList<>();

    /** 纯文本版，推送直接用 */
    private String text;

    @Data
    public static class Item {
        private Long id;
        private String subject;
        private String senderName;
        private String receivedAt;
        private String level;
        private int score;
        private String todoContent;
        private boolean hasAttachment;
        /** 附件文件名，最多两个 */
        private List<String> attachments = new ArrayList<>();
    }
}
