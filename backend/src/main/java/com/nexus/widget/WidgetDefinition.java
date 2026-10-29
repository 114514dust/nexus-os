package com.nexus.widget;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 组件定义（组件目录中的一项）。
 */
@Data
@Entity
@Table(name = "widget_definition")
public class WidgetDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String type;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "default_w", nullable = false)
    private int defaultW;

    @Column(name = "default_h", nullable = false)
    private int defaultH;

    @Column(length = 64)
    private String icon;
}
