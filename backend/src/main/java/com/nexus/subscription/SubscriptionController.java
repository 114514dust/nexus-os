package com.nexus.subscription;

import com.nexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 订阅源接口：CRUD + 手动抓取。
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ApiResponse<List<Subscription>> list() {
        return ApiResponse.ok(subscriptionService.list());
    }

    @PostMapping
    public ApiResponse<Subscription> create(@Valid @RequestBody SubscriptionCreateRequest req) {
        return ApiResponse.ok(subscriptionService.create(req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subscriptionService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/toggle")
    public ApiResponse<Subscription> toggle(@PathVariable Long id) {
        return ApiResponse.ok(subscriptionService.toggle(id));
    }

    @PostMapping("/{id}/fetch")
    public ApiResponse<Integer> fetch(@PathVariable Long id) {
        return ApiResponse.ok(subscriptionService.fetchOne(id));
    }

    @PostMapping("/fetch-all")
    public ApiResponse<Integer> fetchAll() {
        // 前端只关心总新增数，明细由定时任务的推送消费
        int total = subscriptionService.fetchAllEnabled().stream()
                .mapToInt(SubscriptionService.FetchResult::getAdded)
                .sum();
        return ApiResponse.ok(total);
    }
}
