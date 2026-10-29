package com.nexus.link;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建快捷链接请求。
 */
@Data
public class LinkCreateRequest {

    @NotBlank
    @Size(max = 128)
    private String title;

    @NotBlank
    @Size(max = 512)
    private String url;

    private Integer sortOrder;
}
