package com.nexus.todo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 到期提醒用：未完成且到期日不晚于给定日期（含逾期）。
     * 排序交给调用方，逾期与今天的混在一起时按日期升序最自然。
     */
    List<Todo> findByDoneFalseAndDueDateLessThanEqualOrderByDueDateAsc(LocalDate date);
}
