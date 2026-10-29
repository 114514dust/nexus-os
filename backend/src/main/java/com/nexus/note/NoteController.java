package com.nexus.note;

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
 * 便签接口。
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public ApiResponse<List<Note>> list() {
        return ApiResponse.ok(noteService.list());
    }

    @PostMapping
    public ApiResponse<Note> create(@Valid @RequestBody NoteCreateRequest req) {
        return ApiResponse.ok(noteService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Note> update(@PathVariable Long id, @Valid @RequestBody NoteUpdateRequest req) {
        return ApiResponse.ok(noteService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return ApiResponse.ok();
    }
}
