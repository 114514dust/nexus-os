package com.nexus.summary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 每日总结仓储。
 */
public interface SummaryRepository extends JpaRepository<Summary, Long> {

    Optional<Summary> findBySummaryDate(LocalDate summaryDate);

    Optional<Summary> findTopByOrderBySummaryDateDesc();
}
