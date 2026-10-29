package com.nexus.summary;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 每日总结接口：手动触发消化 + 查询最新总结。
 */
@RestController
@RequestMapping("/api/summaries")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    /** 手动触发消化（可选指定日期，默认今天） */
    @PostMapping("/digest")
    public ApiResponse<Summary> digest(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return ApiResponse.ok(summaryService.digest(target));
    }

    /** 查询最新一份总结 */
    @GetMapping("/latest")
    public ApiResponse<Summary> latest() {
        return ApiResponse.ok(summaryService.latest());
    }
}
