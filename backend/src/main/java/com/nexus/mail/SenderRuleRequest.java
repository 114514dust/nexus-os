package com.nexus.mail;

import lombok.Data;

/**
 * 规则的新增/修改请求体。
 * 不复用实体：实体里有 hitCount / createdAt 这类只由服务端维护的字段，
 * 让客户端能提交它们只会制造「命中数被改回去」这种怪事。
 */
@Data
public class SenderRuleRequest {

    /** 地址片段、域名或发件人显示名 */
    private String pattern;

    /** important / watch / ignore */
    private String action;

    private String note;
}
