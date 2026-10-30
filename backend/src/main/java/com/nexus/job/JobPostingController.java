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
 * 校招岗位接口。
 *
 * 与 /api/jobs 分开而不是复用一个控制器：两者的写操作语义完全不同
 * （岗位是「外部数据」，投递是「我的状态」），混在一起会让前端调用歧义。
 */
@RestController
@RequestMapping("/api/postings")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService service;

    @GetMapping
    public ApiResponse<List<PostingView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false, defaultValue = "false") boolean onlyMatched) {
        return ApiResponse.ok(service.list(keyword, category, scope, onlyMatched));
    }

    @GetMapping("/stats")
    public ApiResponse<PostingStats> stats() {
        return ApiResponse.ok(service.stats());
    }

    /** 手动刷新抓取；定时任务也走同一个服务方法 */
    @PostMapping("/refresh")
    public ApiResponse<JobPostingService.RefreshResult> refresh() {
        return ApiResponse.ok(service.refresh());
    }

    @PostMapping
    public ApiResponse<PostingView> create(@Valid @RequestBody PostingCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    /** 收藏 / 忽略 */
    @PutMapping("/{id}")
    public ApiResponse<PostingView> update(@PathVariable Long id,
                                           @RequestBody PostingUpdateRequest req) {
        return ApiResponse.ok(service.update(id, req.getStarred(), req.getIgnored()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /** 一键加入求职追踪：岗位 → 投递，并回填关联 */
    @PostMapping("/{id}/apply")
    public ApiResponse<JobPostingService.ApplyResult> apply(@PathVariable Long id,
                                                            @RequestBody(required = false) PostingApplyRequest req) {
        return ApiResponse.ok(service.apply(id, req));
    }

    @GetMapping("/prefs")
    public ApiResponse<PostingPrefs> prefs() {
        return ApiResponse.ok(service.getPrefs());
    }

    @PutMapping("/prefs")
    public ApiResponse<PostingPrefs> savePrefs(@RequestBody PostingPrefs prefs) {
        return ApiResponse.ok(service.savePrefs(prefs));
    }
}
