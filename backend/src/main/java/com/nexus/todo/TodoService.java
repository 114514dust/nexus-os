package com.nexus.todo;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办服务。
 */
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    @Transactional(readOnly = true)
    public List<Todo> list() {
        return todoRepository.findAll(Sort.by("createdAt").ascending());
    }

    @Transactional
    public Todo create(TodoCreateRequest req) {
        Todo todo = new Todo();
        todo.setContent(req.getContent());
        todo.setDone(false);
        todo.setCreatedAt(LocalDateTime.now());
        return todoRepository.save(todo);
    }

    @Transactional
    public Todo update(Long id, TodoUpdateRequest req) {
        Todo todo = getOrThrow(id);
        if (req.getContent() != null) {
            todo.setContent(req.getContent());
        }
        if (req.getDone() != null) {
            todo.setDone(req.getDone());
        }
        return todo;
    }

    @Transactional
    public void delete(Long id) {
        Todo todo = getOrThrow(id);
        todoRepository.delete(todo);
    }

    private Todo getOrThrow(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "待办不存在"));
    }
}
