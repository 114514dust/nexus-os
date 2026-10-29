package com.nexus.setting;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 应用设置项（key-value 结构），如 LLM API Key / Base URL / Model。
 */
@Data
@Entity
@Table(name = "app_setting")
public class Setting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 64)
    private String key;

    @Column(name = "setting_value", length = 1024)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
