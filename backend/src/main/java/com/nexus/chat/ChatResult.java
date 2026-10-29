package com.nexus.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Agent 对话服务的返回结果。
 */
@Data
@AllArgsConstructor
public class ChatResult {

    private String reply;

    private String intent;
}
