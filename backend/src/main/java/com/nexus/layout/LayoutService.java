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
 * 工作台布局服务（单用户，id 固定 1）。
 */
@Service
@RequiredArgsConstructor
public class LayoutService {

    private static final long DEFAULT_LAYOUT_ID = 1L;
    private static final TypeReference<List<LayoutItemDto>> ITEM_LIST = new TypeReference<>() {};

    private final LayoutRepository layoutRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LayoutDto get() {
        Layout layout = layoutRepository.findById(DEFAULT_LAYOUT_ID)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "布局不存在"));
        LayoutDto dto = new LayoutDto();
        dto.setItems(parse(layout.getItemsJson()));
        return dto;
    }

    @Transactional
    public LayoutDto save(LayoutDto dto) {
        Layout layout = layoutRepository.findById(DEFAULT_LAYOUT_ID).orElseGet(Layout::new);
        layout.setId(DEFAULT_LAYOUT_ID);
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
