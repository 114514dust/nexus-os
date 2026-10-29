package com.nexus.algorithm;

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
 * 高频算法题接口。
 */
@RestController
@RequestMapping("/api/algorithms")
@RequiredArgsConstructor
public class AlgorithmQuestionController {

    private final AlgorithmQuestionService service;

    @GetMapping
    public ApiResponse<List<AlgorithmQuestion>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(service.list(keyword, difficulty, sort));
    }

    @PostMapping
    public ApiResponse<AlgorithmQuestion> create(@Valid @RequestBody AlgorithmCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AlgorithmQuestion> update(@PathVariable Long id,
                                                 @Valid @RequestBody AlgorithmUpdateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
