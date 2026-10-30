package com.nexus.event;

import java.util.Arrays;
import java.util.List;

/**
 * 数据变更的「领域」标识，前后端共用的约定。
 *
 * 每个 domain 对应工作台里的一个组件。AI 工具、手动操作、定时抓取
 * 改的都是同一份数据，因此都发同一种事件。
 */
public final class ChangeDomain {

    public static final String TODO = "todo";
    public static final String NOTE = "note";
    public static final String LINK = "link";
    public static final String SUBSCRIPTION = "subscription";
    public static final String MESSAGE = "message";
    public static final String SUMMARY = "summary";
    public static final String ALGORITHM = "algorithm";
    public static final String JOB = "job";
    public static final String POSTING = "posting";
    public static final String MAIL = "mail";

    public static final List<String> ALL = Arrays.asList(
            TODO, NOTE, LINK, SUBSCRIPTION, MESSAGE, SUMMARY, ALGORITHM, JOB, POSTING, MAIL);

    private ChangeDomain() {
    }
}
