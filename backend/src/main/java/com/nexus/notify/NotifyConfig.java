package com.nexus.notify;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 推送专用的 RestTemplate。
 *
 * 超时给得比业务接口短：群机器人不可达时不能把定时任务线程长时间挂住。
 */
@Configuration
public class NotifyConfig {

    @Bean
    public RestTemplate notifyRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
