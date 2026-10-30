package com.nexus.chat;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 对话记忆仓储。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
}
