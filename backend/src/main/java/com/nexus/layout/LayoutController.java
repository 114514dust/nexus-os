package com.nexus.layout;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 工作台布局接口。
 */
@RestController
@RequestMapping("/api/layout")
@RequiredArgsConstructor
public class LayoutController {

    private final LayoutService layoutService;

    @GetMapping
    public ApiResponse<LayoutDto> get() {
        return ApiResponse.ok(layoutService.get());
    }

    @PutMapping
    public ApiResponse<LayoutDto> save(@Valid @RequestBody LayoutDto dto) {
        return ApiResponse.ok(layoutService.save(dto));
    }
}
