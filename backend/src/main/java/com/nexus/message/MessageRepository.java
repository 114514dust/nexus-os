package com.nexus.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息仓储。
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByReceivedAtBetween(LocalDateTime start, LocalDateTime end);

    Page<Message> findBySource(String source, Pageable pageable);

    Page<Message> findByMsgReadFalse(Pageable pageable);

    Page<Message> findBySourceAndMsgReadFalse(String source, Pageable pageable);

    long countByMsgReadFalse();

    /**
     * 按来源聚合条数与未读数。
     * 返回 [source, total, unread]；聚合在派生方法里表达不出来，走 @Query 最省事。
     */
    @Query("select m.source, count(m), sum(case when m.msgRead = false then 1 else 0 end) "
            + "from Message m group by m.source order by count(m) desc")
    List<Object[]> statsBySource();
}
