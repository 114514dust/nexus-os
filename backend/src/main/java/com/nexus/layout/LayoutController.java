package com.nexus.layout;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 页面布局接口（按页面维度读写）。
 */
@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class LayoutController {

    private final LayoutService layoutService;

    @GetMapping("/{pageId}/layout")
    public ApiResponse<LayoutDto> get(@PathVariable Long pageId) {
        return ApiResponse.ok(layoutService.get(pageId));
    }

    @PutMapping("/{pageId}/layout")
    public ApiResponse<LayoutDto> save(@PathVariable Long pageId, @Valid @RequestBody LayoutDto dto) {
        return ApiResponse.ok(layoutService.save(pageId, dto));
    }
}
