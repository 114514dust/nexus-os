package com.nexus.common;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果。
 */
@Data
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int size;
}
