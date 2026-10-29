package com.nexus.message;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 接收消息请求（通用 webhook 入口）。
 * 任何第三方来源只需把消息转成该结构 POST 进来即可入流。
 */
@Data
public class MessageCreateRequest {

    @NotBlank
    @Size(max = 32)
    private String source;

    @Size(max = 64)
    private String channel;

    @Size(max = 128)
    private String sender;

    @NotBlank
    private String content;
}
