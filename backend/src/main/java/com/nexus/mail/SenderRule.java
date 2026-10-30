package com.nexus.mail;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 发件人规则：让用户用一句「学校就业中心的一律重要」调整判定结果。
 *
 * 为什么需要它：关键词打分对所有发件人一视同仁，但人不是——
 * 同样是「通知」两个字，来自教务处和来自某个商城的分量完全不同。
 * 规则就是把这层判断显式交给用户，而不是让他在每次误判后去改关键词表。
 *
 * 三种动作覆盖了全部实际需求：
 * - {@link #IMPORTANT} 一律重要（达到高置信度，会按需自动建待办）
 * - {@link #WATCH} 至少进「可关注」列表，但不自动建待办（留待用户点一下）
 * - {@link #IGNORE} 不提醒，直接沉底
 */
@Data
@Entity
@Table(name = "mail_sender_rule")
public class SenderRule {

    /** 强制重要 */
    public static final String IMPORTANT = "important";

    /** 至少可关注 */
    public static final String WATCH = "watch";

    /** 不提醒 */
    public static final String IGNORE = "ignore";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 地址片段、域名或发件人显示名，大小写不敏感 */
    @Column(nullable = false, length = 128)
    private String pattern;

    @Column(nullable = false, length = 16)
    private String action;

    /** 备注，方便日后想起来「这条规则当初是为什么加的」 */
    @Column(length = 128)
    private String note;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "last_hit_at")
    private LocalDateTime lastHitAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static boolean isValidAction(String action) {
        return IMPORTANT.equals(action) || WATCH.equals(action) || IGNORE.equals(action);
    }

    public boolean matches(String addr, String name) {
        String p = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) {
            return false;
        }
        return lower(addr).contains(p) || lower(name).contains(p);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
