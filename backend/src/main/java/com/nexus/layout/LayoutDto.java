package com.nexus.layout;

import lombok.Data;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作台布局传输对象。
 */
@Data
public class LayoutDto {

    @Valid
    private List<LayoutItemDto> items = new ArrayList<>();
}
