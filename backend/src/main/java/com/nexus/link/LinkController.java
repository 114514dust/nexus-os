package com.nexus.link;

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
 * 快捷链接接口。
 */
@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;

    @GetMapping
    public ApiResponse<List<Link>> list() {
        return ApiResponse.ok(linkService.list());
    }

    @PostMapping
    public ApiResponse<Link> create(@Valid @RequestBody LinkCreateRequest req) {
        return ApiResponse.ok(linkService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Link> update(@PathVariable Long id, @Valid @RequestBody LinkUpdateRequest req) {
        return ApiResponse.ok(linkService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        linkService.delete(id);
        return ApiResponse.ok();
    }
}
