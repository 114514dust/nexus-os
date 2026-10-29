package com.nexus.algorithm;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 高频算法题（对标 CodeTop 的面试高频题）。
 *
 * 两个维度：
 * - frequency：题目本身在面试中的出现频率（热度），用于默认排序；
 * - star：用户自评星级（0~5），表达个人掌握程度 / 重要性。
 */
@Data
@Entity
@Table(name = "algorithm_question")
public class AlgorithmQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String title;

    /** 难度：easy / medium / hard */
    @Column(nullable = false, length = 16)
    private String difficulty;

    @Column(name = "leetcode_url", nullable = false, length = 512)
    private String leetcodeUrl;

    /** 面试出现频率（热度） */
    @Column(nullable = false)
    private int frequency;

    /** 用户自评星级 0~5 */
    @Column(nullable = false)
    private int star;

    /** 解题思路 / 代码笔记 */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
