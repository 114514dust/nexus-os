package com.nexus.todo;

import lombok.Data;

import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 更新待办请求，字段为 null 表示不更新。
 */
@Data
public class TodoUpdateRequest {

    @Size(max = 512)
    private String content;

    private Boolean done;

    /** 到期日；null 表示不更新 */
    private LocalDate dueDate;

    /**
     * 显式清除到期日。
     * 本项目约定「null = 不更新」，没有这个标记就永远没办法把日期改回空。
     */
    private Boolean clearDueDate;
}
