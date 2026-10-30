package com.nexus.note;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 创建便签请求。
 *
 * 注意：content 允许为空。便签组件的新建交互是「先建一个空白草稿，
 * 用户再直接在文本框里输入」，因此不能用 @NotBlank 拦住空内容。
 */
@Data
public class NoteCreateRequest {

    @Size(max = 5000, message = "便签内容不能超过 5000 字")
    private String content;
}
