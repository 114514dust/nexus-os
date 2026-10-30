package com.nexus.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailAttachmentRepository extends JpaRepository<MailAttachment, Long> {

    List<MailAttachment> findByMailIdOrderByPartIndexAsc(Long mailId);

    List<MailAttachment> findByMailIdInOrderByMailIdAscPartIndexAsc(List<Long> mailIds);

    /** 删邮件时连带清掉附件记录（文件在 service 里删） */
    List<MailAttachment> deleteByMailId(Long mailId);
}
