package com.nexus.job;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 进度时间线的一条记录。
 *
 * 每次阶段变化自动落一条，用户也可以单独加一条纯备注（比如「面试官重点问了 JVM」），
 * 这样时间线既是进度轨迹，也是复盘素材。
 */
@Data
@Entity
@Table(name = "job_event")
public class JobEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    /** 记录发生时所在的阶段 */
    @Column(nullable = false, length = 24)
    private String stage;

    @Column(length = 512)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
