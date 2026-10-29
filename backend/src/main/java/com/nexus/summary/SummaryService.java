package com.nexus.summary;

import com.nexus.message.Message;
import com.nexus.message.MessageRepository;
import com.nexus.note.Note;
import com.nexus.note.NoteRepository;
import com.nexus.todo.Todo;
import com.nexus.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每日总结服务：聚合当天数据 -> 调 Agent 生成总结 -> 落库（幂等 upsert）。
 */
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final SummaryRepository summaryRepository;
    private final MessageRepository messageRepository;
    private final TodoRepository todoRepository;
    private final NoteRepository noteRepository;
    private final DigestClient digestClient;

    /**
     * 对指定日期执行「消化」，生成/更新当天总结。同一日期重复执行会覆盖（幂等）。
     */
    @Transactional
    public Summary digest(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Message> messages = messageRepository.findByReceivedAtBetween(start, end);
        List<Todo> todos = todoRepository.findByCreatedAtBetween(start, end);
        List<Note> notes = noteRepository.findByCreatedAtBetween(start, end);

        DigestResult result = digestClient.summarize(
                date,
                messages.stream().map(this::toMessageMap).collect(Collectors.toList()),
                todos.stream().map(this::toTodoMap).collect(Collectors.toList()),
                notes.stream().map(this::toNoteMap).collect(Collectors.toList())
        );

        Summary summary = summaryRepository.findBySummaryDate(date).orElseGet(Summary::new);
        summary.setSummaryDate(date);
        summary.setContent(result.getSummary());
        summary.setMessageCount(messages.size());
        summary.setTodoCount(todos.size());
        summary.setNoteCount(notes.size());
        summary.setLlmMode(result.getLlm());
        if (summary.getCreatedAt() == null) {
            summary.setCreatedAt(LocalDateTime.now());
        }
        return summaryRepository.save(summary);
    }

    @Transactional(readOnly = true)
    public Summary latest() {
        return summaryRepository.findTopByOrderBySummaryDateDesc().orElse(null);
    }

    private Map<String, Object> toMessageMap(Message m) {
        Map<String, Object> map = new HashMap<>();
        map.put("source", m.getSource());
        map.put("sender", m.getSender() == null ? "" : m.getSender());
        map.put("content", m.getContent());
        return map;
    }

    private Map<String, Object> toTodoMap(Todo t) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", t.getContent());
        map.put("done", t.isDone());
        return map;
    }

    private Map<String, Object> toNoteMap(Note n) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", n.getContent());
        return map;
    }
}
