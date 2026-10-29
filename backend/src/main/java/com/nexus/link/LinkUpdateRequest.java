package com.nexus.link;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 更新快捷链接请求，字段为 null 表示不更新。
 */
@Data
public class LinkUpdateRequest {

    @Size(max = 128)
    private String title;

    @Size(max = 512)
    private String url;

    private Integer sortOrder;
}
