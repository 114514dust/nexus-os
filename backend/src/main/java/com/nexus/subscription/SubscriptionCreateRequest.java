package com.nexus.subscription;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 新建订阅请求。
 */
@Data
public class SubscriptionCreateRequest {

    @NotBlank
    @Pattern(regexp = "rss|bilibili", message = "type 必须为 rss 或 bilibili")
    private String type;

    @NotBlank
    @Size(max = 128)
    private String name;

    /** rss 类型填 feed 地址；bilibili 类型填 UP 主 uid */
    @NotBlank
    @Size(max = 512)
    private String url;
}
