package com.nexus.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话服务：把对话历史作为「记忆」持久化，让 AI 跨会话记住上下文。
 *
 * 设计：后端是对话历史的唯一权威来源——
 * 前端只发 message，这里读最近历史喂给 Python，再把问答落库。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 每次喂给 LLM 的最近历史条数上限 */
    private static final int CONTEXT_LIMIT = 20;
    /** 单次查询历史条数上限（防前端传超大 limit） */
    private static final int MAX_LIMIT = 200;

    private final ChatClient chatClient;
    private final ChatMessageRepository repository;

    /** 对话：读历史 -> 调 Agent -> 落库，返回回复 */
    @Transactional
    public ChatResult chat(String message) {
        List<ChatMessageDto> history = recentHistory(CONTEXT_LIMIT);
        ChatResult result = chatClient.chat(message, history);

        LocalDateTime now = LocalDateTime.now();

        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent(message);
        user.setCreatedAt(now);
        repository.save(user);

        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(result.getReply());
        assistant.setIntent(result.getIntent());
        assistant.setCreatedAt(now);
        repository.save(assistant);

        return result;
    }

    /** 查询最近 limit 条历史（时间正序） */
    @Transactional(readOnly = true)
    public List<ChatMessage> history(int limit) {
        return latest(limit);
    }

    /** 清空对话记忆 */
    @Transactional
    public void clear() {
        repository.deleteAll();
    }

    private List<ChatMessageDto> recentHistory(int limit) {
        List<ChatMessageDto> dtos = new ArrayList<>();
        for (ChatMessage m : latest(limit)) {
            ChatMessageDto dto = new ChatMessageDto();
            dto.setRole(m.getRole());
            dto.setContent(m.getContent());
            dtos.add(dto);
        }
        return dtos;
    }

    /** 按 id 降序取最近 N 条，再反转为时间正序返回 */
    private List<ChatMessage> latest(int limit) {
        int safe = Math.max(1, Math.min(limit, MAX_LIMIT));
        // getContent() 返回不可变列表，需复制一份才能 reverse
        List<ChatMessage> recent = new ArrayList<>(repository
                .findAll(PageRequest.of(0, safe, Sort.by("id").descending()))
                .getContent());
        Collections.reverse(recent);
        return recent;
    }
}
