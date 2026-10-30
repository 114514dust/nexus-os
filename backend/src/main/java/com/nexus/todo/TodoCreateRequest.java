package com.nexus.todo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 创建待办请求。
 */
@Data
public class TodoCreateRequest {

    @NotBlank
    @Size(max = 512)
    private String content;

    /** 到期日，可空（不传 / null 都表示不设期限） */
    private LocalDate dueDate;
}
