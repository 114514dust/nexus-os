package com.nexus.job;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 追加一条进度备注（不改阶段）。
 */
@Data
public class JobNoteRequest {

    @NotBlank(message = "备注内容不能为空")
    @Size(max = 512)
    private String note;
}
