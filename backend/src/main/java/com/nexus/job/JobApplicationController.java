package com.nexus.job;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 求职追踪接口。
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService service;

    @GetMapping
    public ApiResponse<List<JobApplication>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.list(keyword, category, stage, scope, sort));
    }

    /** 概览统计：基于全量数据，不受列表筛选影响 */
    @GetMapping("/stats")
    public ApiResponse<JobStats> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/{id}")
    public ApiResponse<JobDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    public ApiResponse<JobApplication> create(@Valid @RequestBody JobCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobApplication> update(@PathVariable Long id,
                                              @Valid @RequestBody JobUpdateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    /** 一键推进到下一阶段；body 可选，可带一条本次进展的备注 */
    @PostMapping("/{id}/advance")
    public ApiResponse<JobApplication> advance(@PathVariable Long id,
                                               @RequestBody(required = false) JobNoteRequest req) {
        return ApiResponse.ok(service.advance(id, req == null ? null : req.getNote()));
    }

    /** 只加一条进展备注，不改阶段 */
    @PostMapping("/{id}/events")
    public ApiResponse<JobDetail> addNote(@PathVariable Long id,
                                          @Valid @RequestBody JobNoteRequest req) {
        return ApiResponse.ok(service.addNote(id, req.getNote()));
    }

    /** 删除时间线里的一条进展记录（不影响当前阶段） */
    @DeleteMapping("/{id}/events/{eventId}")
    public ApiResponse<JobDetail> deleteNote(@PathVariable Long id, @PathVariable Long eventId) {
        return ApiResponse.ok(service.deleteEvent(id, eventId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
