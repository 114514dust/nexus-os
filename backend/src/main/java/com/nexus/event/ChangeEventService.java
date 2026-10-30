package com.nexus.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 变更广播中心：维护所有前端的 SSE 长连接，数据一变就推一条事件下去。
 *
 * 设计要点：
 * - **CopyOnWriteArrayList** 而不是普通 List——订阅/断开是低频写，广播是高频读，
 *   写时复制能保证广播遍历时不会因为并发增删抛 ConcurrentModificationException；
 * - **心跳**：长连接会被浏览器或中间代理静默掐断（通常 60s 空闲），
 *   每 25s 推一个 ping 事件保活，比依赖前端重连更省事；
 * - 单实例内存广播。若将来多实例部署，这里换成 Redis Pub/Sub 即可，
 *   对上层（Notifier / 前端协议）无影响。
 */
@Slf4j
@Service
public class ChangeEventService {

    /** 事件名：前端用 addEventListener('change', ...) 接收 */
    public static final String EVENT_CHANGE = "change";
    /** 连接建立时的握手事件，前端据此确认通道可用 */
    public static final String EVENT_HELLO = "hello";
    /** 心跳事件，客户端可忽略 */
    public static final String EVENT_PING = "ping";

    /** timeout = 0 表示不超时，长连接的断开交给心跳与 onCompletion 处理 */
    private static final long NO_TIMEOUT = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** 前端建立订阅：注册 emitter 并立刻握手。 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            Map<String, Object> hello = new LinkedHashMap<>();
            hello.put("domains", ChangeDomain.ALL);
            emitter.send(SseEmitter.event().name(EVENT_HELLO).data(hello, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 握手失败说明连接已经废了，直接回收
            emitters.remove(emitter);
        }
        log.info("SSE 订阅建立，当前连接数={}", emitters.size());
        return emitter;
    }

    /** 广播一条领域变更事件。 */
    public void broadcast(String domain) {
        if (emitters.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("domain", domain);
        payload.put("at", Instant.now().toString());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_CHANGE).data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // 发送失败说明这条连接已断开，移除即可（前端 EventSource 会自动重连）
                emitters.remove(emitter);
            }
        }
    }

    /** 心跳保活：防止长连接被代理或浏览器按空闲超时掐断。 */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_PING).data("1"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /** 当前活跃订阅数（便于排查） */
    public int subscriberCount() {
        return emitters.size();
    }
}
