package com.nexus.message;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息列表结果：一页消息 + 未读总数 + 各来源统计。
 *
 * 统计跟列表放在同一个响应里，前端切换来源筛选时不用再打第二个请求。
 */
@Data
public class MessagePage {

    private List<MessageView> list = new ArrayList<>();
    private long total;
    private int page;
    private int size;
    /** 全部未读数（不受当前筛选影响） */
    private long unread;
    /** 各来源的条数与未读数，用于顶部筛选标签 */
    private List<SourceStat> sources = new ArrayList<>();

    @Data
    public static class SourceStat {

        private String key;
        private String label;
        private long count;
        private long unread;

        public SourceStat(String key, long count, long unread) {
            this.key = key;
            this.label = MessageView.label(key);
            this.count = count;
            this.unread = unread;
        }
    }
}
