package com.nexus.page;

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
 * 页面接口。
 */
@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @GetMapping
    public ApiResponse<List<Page>> list() {
        return ApiResponse.ok(pageService.list());
    }

    @PostMapping
    public ApiResponse<Page> create(@Valid @RequestBody PageRequest req) {
        return ApiResponse.ok(pageService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Page> rename(@PathVariable Long id, @Valid @RequestBody PageRequest req) {
        return ApiResponse.ok(pageService.rename(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pageService.delete(id);
        return ApiResponse.ok(null);
    }
}
