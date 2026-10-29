package com.nexus.setting;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用设置接口。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ApiResponse<LlmSettingsDto> get() {
        return ApiResponse.ok(settingsService.get());
    }

    @PutMapping
    public ApiResponse<LlmSettingsDto> save(@RequestBody LlmSettingsDto dto) {
        return ApiResponse.ok(settingsService.save(dto));
    }
}
