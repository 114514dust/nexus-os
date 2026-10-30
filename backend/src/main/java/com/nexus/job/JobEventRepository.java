package com.nexus.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobEventRepository extends JpaRepository<JobEvent, Long> {

    List<JobEvent> findByJobIdOrderByOccurredAtDesc(Long jobId);

    @Modifying
    @Query("delete from JobEvent e where e.jobId = :jobId")
    void deleteByJobId(@Param("jobId") Long jobId);
}
