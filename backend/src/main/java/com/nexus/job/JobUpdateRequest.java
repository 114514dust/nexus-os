package com.nexus.job;

import lombok.Data;

import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 更新投递记录，字段为 null 表示不更新。
 *
 * stage 变化时会自动写一条时间线，备注取 stageNote。
 */
@Data
public class JobUpdateRequest {

    @Size(max = 128)
    private String company;

    @Size(max = 128)
    private String jobTitle;

    private String category;

    private String stage;

    @Size(max = 1024)
    private String url;

    @Size(max = 256)
    private String nextAction;

    private LocalDate nextAt;

    /** 显式清空 nextAt。null 表示「不改」，故清空需要这个独立开关 */
    private Boolean clearNextAt;

    private Boolean starred;

    private String note;

    @Size(max = 512)
    private String stageNote;
}
