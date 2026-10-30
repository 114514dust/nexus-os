package com.nexus.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MailMessageRepository extends JpaRepository<MailMessage, Long> {

    Optional<MailMessage> findByAccountAndImapUid(String account, Long imapUid);

    /**
     * 增量抓取时一次性把已知 UID 拿回来，避免每封信查一次库。
     * 必须写成 @Query：派生查询会把返回的 Long 当成实体类型去转换，直接报错。
     */
    @Query("select m.imapUid from MailMessage m where m.account = :account")
    List<Long> findImapUidsByAccount(@Param("account") String account);

    /** 列表页只关心最近的邮件，老邮件不进内存 */
    List<MailMessage> findTop500ByOrderByReceivedAtDesc();

    List<MailMessage> findTop200ByIgnoredFalseAndImportantLevelNotOrderByReceivedAtDesc(String level);

    List<MailMessage> findByTodoId(Long todoId);

    long countByIgnoredFalseAndImportantLevel(String level);

    long countByIgnoredFalseAndImportantLevelNot(String level);

    /** 已关联待办的邮件。统计时要再核一遍待办是否还在（用户可能删掉了） */
    List<MailMessage> findByTodoIdNotNull();

    long countByIgnoredFalse();

    /** 每日摘要按「收到时间落在某一天」取数，区间左闭右开 */
    List<MailMessage> findByReceivedAtGreaterThanEqualAndReceivedAtLessThanOrderByReceivedAtAsc(
            LocalDateTime from, LocalDateTime to);
}
