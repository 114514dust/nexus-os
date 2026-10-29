package com.nexus.note;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 便签仓储。
 */
public interface NoteRepository extends JpaRepository<Note, Long> {
}
