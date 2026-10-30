package com.nexus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用配置，启动时集中校验、快速失败。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "nexus")
public class AppProperties {

    private final Cors cors = new Cors();

    private final Mail mail = new Mail();

    @Data
    public static class Cors {
        @NotEmpty(message = "nexus.cors.allowed-origins 不能为空")
        private List<String> allowedOrigins = new ArrayList<>();
    }

    /** 邮箱相关（cron 由 @Scheduled 直接读，这里只管附件落盘） */
    @Data
    public static class Mail {

        /** 附件保存目录，相对路径以应用启动目录为基准 */
        private String attachmentDir = "./data/mail-attachments";

        /** 单个附件大小上限（MB）。超了直接拒绝，避免一个 2G 的压缩包把盘写满 */
        private int attachmentMaxMb = 25;
    }
}
