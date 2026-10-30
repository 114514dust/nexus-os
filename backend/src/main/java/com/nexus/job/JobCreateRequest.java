package com.nexus.job;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 新建投递记录。
 *
 * 时间线不是必填——秋招海投时用户只想快速记一行「投了哪家」，
 * 如果强制填日期/下一步，录入成本会让人放弃使用。所以这里只有 company 是硬约束。
 */
@Data
public class JobCreateRequest {

    @NotBlank(message = "公司名称不能为空")
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

    private Boolean starred;

    private String note;

    /** 首条时间线的备注（可空），如「内推」「Boss 直聘投递」 */
    @Size(max = 512)
    private String firstNote;
}
