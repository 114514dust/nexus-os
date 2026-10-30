package com.nexus.notify;

/**
 * 推送渠道抽象。
 *
 * 新增渠道只需实现本接口并注册为 Spring Bean，NotifyService 无需改动。
 */
public interface NotifyChannel {

    /** 渠道标识，与配置项 notify.channel 对应 */
    String type();

    /**
     * 发送一条文本消息。
     *
     * 注意：群机器人在 HTTP 200 的情况下也可能返回业务错误（签名错误、频率超限等），
     * 实现方必须解析响应体，失败时抛出异常，否则配置错了会「悄悄成功」。
     *
     * @param text   已拼好的正文
     * @param config 当前推送配置（取 webhookUrl / secret）
     */
    void send(String text, NotifySettingsDto config) throws Exception;
}
