package com.nexus.note;

import lombok.Data;

/**
 * 更新便签请求，字段为 null 表示不更新。
 */
@Data
public class NoteUpdateRequest {

    private String content;
}
