package com.nexus.notify;

import com.nexus.common.ApiException;
import com.nexus.common.ApiResponse;
import com.nexus.common.ErrorCode;
import com.nexus.setting.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息推送配置与测试接口。
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final SettingsService settingsService;
    private final NotifyService notifyService;

    @GetMapping("/settings")
    public ApiResponse<NotifySettingsDto> get() {
        return ApiResponse.ok(settingsService.getNotify());
    }

    @PutMapping("/settings")
    public ApiResponse<NotifySettingsDto> save(@RequestBody NotifySettingsDto dto) {
        return ApiResponse.ok(settingsService.saveNotify(dto));
    }

    /**
     * 发送一条测试消息。
     * 同步执行并把失败原因转成业务错误——配置错了必须当场告诉用户，
     * 否则用户只能干等定时任务才发现推不出去。
     */
    @PostMapping("/test")
    public ApiResponse<Void> test() {
        try {
            notifyService.sendTest();
            return ApiResponse.ok();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "推送失败：" + e.getMessage());
        }
    }
}
