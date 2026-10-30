package com.nexus.job;

import java.util.List;

/**
 * 求职进度阶段。
 *
 * 设计取舍：阶段做成**有序线性**的，而不是自由状态机。
 * 求职的实际流程高度标准化（投递 → 笔试 → 一面 → 二面 → HR 面 → Offer），
 * 线性顺序让「一键推进到下一阶段」成为可能——这是这个组件最常用的操作，
 * 如果做成任意跳转，用户每次都得从下拉框里找，反而更累。
 *
 * REJECTED 不参与线性推进（它是旁路终态），因此单独排除在 ORDER 之外。
 */
public final class JobStage {

    public static final String WISH = "wish";
    public static final String APPLIED = "applied";
    public static final String WRITTEN = "written";
    public static final String INTERVIEW1 = "interview1";
    public static final String INTERVIEW2 = "interview2";
    public static final String HR = "hr";
    public static final String OFFER = "offer";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";

    /** 线性推进顺序（不含 REJECTED） */
    public static final List<String> ORDER = List.of(
            WISH, APPLIED, WRITTEN, INTERVIEW1, INTERVIEW2, HR, OFFER, ACCEPTED);

    public static final List<String> ALL = List.of(
            WISH, APPLIED, WRITTEN, INTERVIEW1, INTERVIEW2, HR, OFFER, ACCEPTED, REJECTED);

    /** 终态：已入职 / 未通过。列表默认把它们收进「已结束」，避免干扰视线 */
    private static final List<String> CLOSED = List.of(ACCEPTED, REJECTED);

    private JobStage() {
    }

    public static boolean isValid(String stage) {
        return stage != null && ALL.contains(stage);
    }

    /** 流程是否已经结束（已入职 / 未通过）。注意 Offer 仍算「进行中」——还要答复和比较 */
    public static boolean isClosed(String stage) {
        return stage != null && CLOSED.contains(stage);
    }

    /** 是否正在进行面试 */
    public static boolean isInterviewing(String stage) {
        return INTERVIEW1.equals(stage) || INTERVIEW2.equals(stage) || HR.equals(stage);
    }

    /** 是否已经过了「只投递没回音」的阶段（笔试及以上），用于算回复率 */
    public static boolean hasProgress(String stage) {
        return ORDER.indexOf(stage) >= ORDER.indexOf(WRITTEN);
    }

    /** 下一阶段；已是终态则返回 null */
    public static String next(String stage) {
        int idx = ORDER.indexOf(stage);
        if (idx < 0 || idx == ORDER.size() - 1) {
            return null;
        }
        return ORDER.get(idx + 1);
    }

    /** 中文名，用于推送文案 */
    public static String label(String stage) {
        switch (stage == null ? "" : stage) {
            case WISH:
                return "想投";
            case APPLIED:
                return "已投递";
            case WRITTEN:
                return "笔试";
            case INTERVIEW1:
                return "一面";
            case INTERVIEW2:
                return "二面";
            case HR:
                return "HR 面";
            case OFFER:
                return "Offer";
            case ACCEPTED:
                return "已入职";
            case REJECTED:
                return "未通过";
            default:
                return stage;
        }
    }
}
