package com.nexus.summary;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日总结：Agent 消化当天消息/待办/便签后的产物。
 */
@Data
@Entity
@Table(name = "daily_summary")
public class Summary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 总结对应的日期（唯一，一天一份） */
    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "todo_count", nullable = false)
    private int todoCount;

    @Column(name = "note_count", nullable = false)
    private int noteCount;

    /** 生成方式：enabled=真实 LLM，rule-based=规则式降级 */
    @Column(name = "llm_mode", length = 32)
    private String llmMode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
