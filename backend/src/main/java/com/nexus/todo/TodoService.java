package com.nexus.todo;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
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
    private final ChangeEventNotifier notifier;

    @Transactional(readOnly = true)
    public List<Todo> list() {
        return todoRepository.findAll(Sort.by("createdAt").ascending());
    }

    @Transactional
    public Todo create(TodoCreateRequest req) {
        Todo todo = new Todo();
        todo.setContent(req.getContent());
        todo.setDone(false);
        todo.setDueDate(req.getDueDate());
        todo.setCreatedAt(LocalDateTime.now());
        Todo saved = todoRepository.save(todo);
        notifier.notify(ChangeDomain.TODO);
        return saved;
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
        // 清除优先于设置：前端「清空日期」时两个字段可能同时带上，清空是更明确的意图
        if (Boolean.TRUE.equals(req.getClearDueDate())) {
            todo.setDueDate(null);
        } else if (req.getDueDate() != null) {
            todo.setDueDate(req.getDueDate());
        }
        notifier.notify(ChangeDomain.TODO);
        return todo;
    }

    @Transactional
    public void delete(Long id) {
        Todo todo = getOrThrow(id);
        todoRepository.delete(todo);
        notifier.notify(ChangeDomain.TODO);
    }

    private Todo getOrThrow(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "待办不存在"));
    }
}
