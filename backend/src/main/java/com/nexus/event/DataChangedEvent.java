package com.nexus.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据变更事件（Spring 进程内事件）。
 *
 * 业务 Service 写完数据后发它，由 {@link ChangeEventBroadcaster} 在
 * **事务提交之后**转成 SSE 推给前端——见该类的说明。
 */
@Getter
public class DataChangedEvent extends ApplicationEvent {

    private final String domain;

    public DataChangedEvent(Object source, String domain) {
        super(source);
        this.domain = domain;
    }
}
