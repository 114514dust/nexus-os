package com.nexus.layout;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;
import java.util.Map;

/**
 * 布局中的一个组件项。
 */
@Data
public class LayoutItemDto {

    @NotBlank
    private String widgetType;

    @PositiveOrZero
    private int x;

    @PositiveOrZero
    private int y;

    @PositiveOrZero
    private int w;

    @PositiveOrZero
    private int h;

    /** 组件实例配置，随布局项持久化（可配置组件的自定义项）。 */
    private Map<String, Object> config;
}
