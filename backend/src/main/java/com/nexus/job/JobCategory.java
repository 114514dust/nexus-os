package com.nexus.job;

import java.util.List;

/**
 * 投递类型（用户口中的「面试类型」）。
 *
 * 现实中这些维度并不完全正交（秋招 / 实习是批次，国企是企业性质），
 * 但对找工作的人来说，他们就是用这几个词给自己分类的，
 * 所以这里不做学究式拆分，直接按用户语言建枚举。
 */
public final class JobCategory {

    public static final String AUTUMN = "autumn";
    public static final String SPRING = "spring";
    public static final String INTERN = "intern";
    public static final String SOCIAL = "social";
    public static final String SOE = "soe";
    public static final String OTHER = "other";

    public static final List<String> ALL = List.of(AUTUMN, SPRING, INTERN, SOCIAL, SOE, OTHER);

    private JobCategory() {
    }

    public static boolean isValid(String category) {
        return category != null && ALL.contains(category);
    }

    public static String label(String category) {
        switch (category == null ? "" : category) {
            case AUTUMN:
                return "秋招";
            case SPRING:
                return "春招";
            case INTERN:
                return "实习";
            case SOCIAL:
                return "社招";
            case SOE:
                return "国企";
            default:
                return "其他";
        }
    }
}
