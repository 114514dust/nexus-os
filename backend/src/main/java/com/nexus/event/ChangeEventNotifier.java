package com.nexus.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 变更通知入口：业务 Service 只需调一行 {@code notifier.notify(ChangeDomain.TODO)}。
 *
 * 之所以不直接推 SSE，而是先发一个进程内事件，是为了让
 * {@link ChangeEventBroadcaster} 用 {@code @TransactionalEventListener} 把推送
 * 推迟到事务提交之后——否则前端收到通知马上来拉数据，很可能读到尚未提交的旧值。
 */
@Component
@RequiredArgsConstructor
public class ChangeEventNotifier {

    private final ApplicationEventPublisher publisher;

    public void notify(String domain) {
        publisher.publishEvent(new DataChangedEvent(this, domain));
    }
}
