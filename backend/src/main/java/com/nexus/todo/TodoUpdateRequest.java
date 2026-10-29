package com.nexus.todo;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 更新待办请求，字段为 null 表示不更新。
 */
@Data
public class TodoUpdateRequest {

    @Size(max = 512)
    private String content;

    private Boolean done;
}
