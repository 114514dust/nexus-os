package com.nexus.layout;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 工作台布局（单用户，id 固定为 1），items 以 JSON 数组存储。
 */
@Data
@Entity
@Table(name = "layout")
public class Layout {

    @Id
    private Long id;

    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
