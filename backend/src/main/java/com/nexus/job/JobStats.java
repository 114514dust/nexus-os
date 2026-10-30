package com.nexus.job;

import lombok.Data;

/**
 * 求职概览统计。
 *
 * replyRate 的口径是「有笔试及以上推进的占比」——
 * 它比「已读率」更能反映真实情况：只有发来笔试/面试才算真的被回复。
 */
@Data
public class JobStats {

    /** 全部投递数 */
    private int total;

    /** 进行中（未到 Offer / 入职 / 未通过） */
    private int active;

    /** 面试中（一面 / 二面 / HR 面） */
    private int interviewing;

    /** 已拿 Offer（含已入职） */
    private int offers;

    /** 未通过 */
    private int rejected;

    /** 有实质推进的投递数 */
    private int replied;

    /** 回复率百分比 0~100 */
    private int replyRate;

    /** 未来 7 天内有面试 / 待办的数量 */
    private int upcoming;
}
