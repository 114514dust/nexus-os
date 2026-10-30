package com.nexus.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话服务：把对话历史作为「记忆」持久化，让 AI 跨会话记住上下文。
 *
 * 设计：后端是对话历史的唯一权威来源——
 * 前端只发 message，这里读最近历史喂给 Python，再把问答落库。
 *
 * 流式：SSE 推送在独立线程池执行，不占用 Tomcat 请求线程；
 * 落库放在流结束后，保证存进去的是完整回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 每次喂给 LLM 的最近历史条数上限 */
    private static final int CONTEXT_LIMIT = 20;
    /** 单次查询历史条数上限（防前端传超大 limit） */
    private static final int MAX_LIMIT = 200;
    /** 流式 SSE 连接超时（毫秒） */
    private static final long STREAM_TIMEOUT_MS = 300_000L;

    /**
     * 流式对话专用线程池：流式是长连接阻塞操作，绝不能占 Tomcat 请求线程。
     * 用守护线程避免影响应用关闭。
     */
    private static final ExecutorService STREAM_POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "chat-stream");
        t.setDaemon(true);
        return t;
    });

    private final ChatClient chatClient;
    private final ChatMessageRepository repository;

    /** 对话：读历史 -> 调 Agent -> 落库，返回回复 */
    @Transactional
    public ChatResult chat(String message) {
        List<ChatMessageDto> history = recentHistory(CONTEXT_LIMIT);
        ChatResult result = chatClient.chat(message, history);
        saveTurn(message, result);
        return result;
    }

    /**
     * 流式对话：提交到线程池异步执行，方法立刻返回 SseEmitter。
     *
     * 关键取舍——前端断开后**不中断**后端读取：
     * 用户刷新页面或关掉组件时连接会断，此时仍把 Agent 的流读完并落库，
     * 这样回复不会因为一次刷新就丢失，下次打开还能看到完整内容。
     */
    public SseEmitter streamChat(String message) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        STREAM_POOL.execute(() -> runStream(message, emitter));
        return emitter;
    }

    private void runStream(String message, SseEmitter emitter) {
        AtomicBoolean clientGone = new AtomicBoolean(false);
        try {
            List<ChatMessageDto> history = recentHistory(CONTEXT_LIMIT);
            ChatResult result = chatClient.streamChat(
                    message,
                    history,
                    intent -> push(emitter, clientGone, "intent", Map.of("intent", intent)),
                    delta -> push(emitter, clientGone, "delta", Map.of("content", delta)));

            // 流正常读完才落库，避免把半截回复写进记忆污染后续上下文
            saveTurn(message, result);

            if (!clientGone.get()) {
                push(emitter, clientGone, "done",
                        Map.of("reply", result.getReply(), "intent", result.getIntent()));
                emitter.complete();
            }
        } catch (Exception e) {
            log.warn("流式对话失败: {}", e.getMessage());
            if (!clientGone.get()) {
                push(emitter, clientGone, "error",
                        Map.of("message", "对话服务暂时不可用，请稍后重试"));
                emitter.complete();
            }
        }
    }

    /** 推送一条 SSE 事件；连接已断时只做标记，不再抛异常打断读流。 */
    private void push(SseEmitter emitter, AtomicBoolean clientGone, String type, Map<String, Object> data) {
        if (clientGone.get()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.putAll(data);
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 前端已断开：后续不再推送，但让读取循环继续跑完以便落库
            clientGone.set(true);
            log.debug("SSE 连接已断开，转为静默完成: {}", e.getMessage());
        }
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

    /** 落库一轮问答（user + assistant）。 */
    private void saveTurn(String message, ChatResult result) {
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
