package com.nexus.layout;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;

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
}
