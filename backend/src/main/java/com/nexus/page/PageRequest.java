package com.nexus.page;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 页面创建 / 重命名请求。
 */
@Data
public class PageRequest {

    @NotBlank(message = "页面名称不能为空")
    private String name;
}
