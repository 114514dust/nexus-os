package com.nexus.layout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 布局服务：按页面维度读写布局。
 */
@Service
@RequiredArgsConstructor
public class LayoutService {

    private static final TypeReference<List<LayoutItemDto>> ITEM_LIST = new TypeReference<>() {};

    private final LayoutRepository layoutRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LayoutDto get(Long pageId) {
        Layout layout = layoutRepository.findByPageId(pageId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "布局不存在"));
        LayoutDto dto = new LayoutDto();
        dto.setItems(parse(layout.getItemsJson()));
        return dto;
    }

    @Transactional
    public LayoutDto save(Long pageId, LayoutDto dto) {
        Layout layout = layoutRepository.findByPageId(pageId).orElseGet(Layout::new);
        layout.setPageId(pageId);
        layout.setItemsJson(write(dto.getItems()));
        layout.setUpdatedAt(LocalDateTime.now());
        layoutRepository.save(layout);
        return dto;
    }

    private List<LayoutItemDto> parse(String json) {
        try {
            return objectMapper.readValue(json, ITEM_LIST);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String write(List<LayoutItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? new ArrayList<>() : items);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "布局序列化失败");
        }
    }
}
