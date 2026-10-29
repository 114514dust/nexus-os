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

    @Data
    public static class Cors {
        @NotEmpty(message = "nexus.cors.allowed-origins 不能为空")
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
