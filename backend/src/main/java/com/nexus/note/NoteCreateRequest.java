package com.nexus.note;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 创建便签请求。
 */
@Data
public class NoteCreateRequest {

    @NotBlank
    private String content;
}
