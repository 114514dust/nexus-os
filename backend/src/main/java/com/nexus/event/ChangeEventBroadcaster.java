package com.nexus.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 把进程内事件转成 SSE 广播。
 *
 * 用 {@code AFTER_COMMIT} 而不是直接监听，是因为「数据可见」必须早于「通知到达」：
 * 若在事务提交前推送，前端收到事件马上来拉列表，读到的还是旧数据，
 * 表现为"刷新了但没变化"。加上 fallbackExecution = true，
 * 让没有事务上下文的调用（如定时任务直接调用）也能正常推送。
 */
@Component
@RequiredArgsConstructor
public class ChangeEventBroadcaster {

    private final ChangeEventService changeEventService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDataChanged(DataChangedEvent event) {
        changeEventService.broadcast(event.getDomain());
    }
}
