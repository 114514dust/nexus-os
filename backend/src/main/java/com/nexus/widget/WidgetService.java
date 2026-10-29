package com.nexus.widget;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组件目录服务。
 */
@Service
@RequiredArgsConstructor
public class WidgetService {

    private final WidgetRepository widgetRepository;

    @Transactional(readOnly = true)
    public List<WidgetDefinition> list() {
        return widgetRepository.findAll(Sort.by("id").ascending());
    }
}
