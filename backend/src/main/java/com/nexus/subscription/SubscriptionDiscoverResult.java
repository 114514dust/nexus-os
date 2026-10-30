package com.nexus.subscription;

import lombok.Data;

/**
 * 订阅地址识别结果。
 *
 * 用户只需要粘一个网址，识别成功就能直接添加；
 * 失败时 {@link #message} 是要给人看的一句话建议，不是堆栈。
 */
@Data
public class SubscriptionDiscoverResult {

    private boolean ok;
    /** rss / bilibili */
    private String type;
    private String name;
    /** 归一化后的入参：rss 为 feed 地址，bilibili 为 uid */
    private String url;
    /** 识别过程中顺手读到的条目数，让用户知道这个源是活的 */
    private int sampleCount;
    private String message;
    /** 识别说明，例如「博客首页里找到了 atom.xml」 */
    private String hint;

    public static SubscriptionDiscoverResult fail(String message) {
        SubscriptionDiscoverResult r = new SubscriptionDiscoverResult();
        r.ok = false;
        r.message = message;
        return r;
    }
}
