package com.nexus.subscription;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 订阅地址识别请求：只要求一个网址。
 */
@Data
public class SubscriptionDiscoverRequest {

    @NotBlank(message = "请先填写订阅地址")
    @Size(max = 512)
    private String url;
}
