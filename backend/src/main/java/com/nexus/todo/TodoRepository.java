package com.nexus.todo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
