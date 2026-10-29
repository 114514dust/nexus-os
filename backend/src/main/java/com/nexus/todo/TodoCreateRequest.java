package com.nexus.todo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建待办请求。
 */
@Data
public class TodoCreateRequest {

    @NotBlank
    @Size(max = 512)
    private String content;
}
