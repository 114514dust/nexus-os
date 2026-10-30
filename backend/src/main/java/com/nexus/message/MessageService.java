package com.nexus.message;

import com.nexus.common.PageResult;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消息服务：接收外部消息 + 分页查询。
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChangeEventNotifier notifier;

    @Transactional
    public Message receive(MessageCreateRequest req) {
        Message msg = new Message();
        msg.setSource(req.getSource());
        msg.setChannel(req.getChannel());
        msg.setSender(req.getSender());
        msg.setContent(req.getContent());
        msg.setReceivedAt(LocalDateTime.now());
        msg.setProcessed(false);
        Message saved = messageRepository.save(msg);
        notifier.notify(ChangeDomain.MESSAGE);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResult<Message> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by("receivedAt").descending());
        Page<Message> p = messageRepository.findAll(pr);

        PageResult<Message> result = new PageResult<>();
        result.setList(p.getContent());
        result.setTotal(p.getTotalElements());
        result.setPage(safePage);
        result.setSize(safeSize);
        return result;
    }
}
