package com.nexus.note;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 便签仓储。
 */
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
