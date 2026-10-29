package com.nexus.message;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 消息仓储。
 */
public interface MessageRepository extends JpaRepository<Message, Long> {
}
