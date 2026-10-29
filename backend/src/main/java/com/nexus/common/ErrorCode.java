package com.nexus.common;

import lombok.Getter;

/**
 * 业务错误码，与 HTTP 状态解耦，客户端据此映射友好文案。
 */
@Getter
public enum ErrorCode {
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    AGENT_ERROR(502, "Agent 服务调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
