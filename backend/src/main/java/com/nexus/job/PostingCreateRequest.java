package com.nexus.job;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 手动添加岗位（抓取覆盖不到的渠道：内推群、公众号、学院就业网…）。
 *
 * 只有公司名必填：来源不明时用户往往只记得「哪家在招」，先记下来比字段齐全重要。
 */
@Data
public class PostingCreateRequest {

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 128)
    private String company;

    @Size(max = 256)
    private String title;

    @Size(max = 2048)
    private String positions;

    @Size(max = 256)
    private String city;

    @Size(max = 24)
    private String category;

    /** 投递链接 / 公告链接 */
    @Size(max = 1024)
    private String url;

    @Size(max = 64)
    private String deadlineText;

    @Size(max = 32)
    private String cohort;
}
