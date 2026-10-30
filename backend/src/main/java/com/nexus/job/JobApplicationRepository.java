package com.nexus.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /** 面试提醒：落在 [from, to] 区间内、且尚未尘埃落定的投递 */
    @Query("select j from JobApplication j where j.nextAt is not null and j.nextAt between :from and :to")
    List<JobApplication> findScheduledBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 跟进提醒：长期没有更新的进行中投递 */
    @Query("select j from JobApplication j where j.updatedAt < :before "
            + "and j.stage not in ('accepted', 'rejected')")
    List<JobApplication> findStaleBefore(@Param("before") LocalDateTime before);
}
