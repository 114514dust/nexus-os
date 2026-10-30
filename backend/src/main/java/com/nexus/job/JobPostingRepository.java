package com.nexus.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Optional<JobPosting> findBySourceAndSourceId(String source, String sourceId);

    List<JobPosting> findBySource(String source);

    List<JobPosting> findByJobId(Long jobId);

    long countByIgnoredFalseAndJobIdIsNull();
}
