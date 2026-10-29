package com.nexus.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息仓储。
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByReceivedAtBetween(LocalDateTime start, LocalDateTime end);
}
