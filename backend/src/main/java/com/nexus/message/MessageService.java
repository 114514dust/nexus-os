package com.nexus.message;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息服务：接收外部消息 + 收件箱式查询（来源筛选、未读、已读标记）。
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

    /**
     * 收件箱列表。
     *
     * @param source     来源筛选，空表示全部
     * @param unreadOnly 只看未读
     */
    @Transactional(readOnly = true)
    public MessagePage list(int page, int size, String source, boolean unreadOnly) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by("receivedAt").descending());
        String src = (source == null || source.isBlank()) ? null : source.trim();

        Page<Message> p;
        if (src == null) {
            p = unreadOnly ? messageRepository.findByMsgReadFalse(pr) : messageRepository.findAll(pr);
        } else {
            p = unreadOnly
                    ? messageRepository.findBySourceAndMsgReadFalse(src, pr)
                    : messageRepository.findBySource(src, pr);
        }

        MessagePage result = new MessagePage();
        List<MessageView> views = new ArrayList<>();
        for (Message m : p.getContent()) {
            views.add(MessageView.of(m));
        }
        result.setList(views);
        result.setTotal(p.getTotalElements());
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setUnread(messageRepository.countByMsgReadFalse());
        result.setSources(sourceStats());
        return result;
    }

    private List<MessagePage.SourceStat> sourceStats() {
        List<MessagePage.SourceStat> stats = new ArrayList<>();
        for (Object[] row : messageRepository.statsBySource()) {
            String key = (String) row[0];
            long total = row[1] == null ? 0L : ((Number) row[1]).longValue();
            long unread = row[2] == null ? 0L : ((Number) row[2]).longValue();
            stats.add(new MessagePage.SourceStat(key, total, unread));
        }
        return stats;
    }

    /**
     * 标记单条已读。
     *
     * 这里刻意不广播 ChangeEvent：前端点开就已经本地更新了，
     * 再广播一次只会让列表整体重载，把用户正在看的位置冲掉。
     * 真正需要广播的是「有新内容进来」（抓取 / webhook / AI）。
     */
    @Transactional
    public void markRead(Long id, boolean read) {
        Message msg = messageRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "消息不存在"));
        if (msg.isMsgRead() != read) {
            msg.setMsgRead(read);
            messageRepository.save(msg);
        }
    }

    /**
     * 全部标记已读。
     *
     * @param source 只清某个来源，空则清全部
     * @return 实际被标记的条数
     */
    @Transactional
    public int markAllRead(String source) {
        String src = (source == null || source.isBlank()) ? null : source.trim();
        PageRequest pageable = PageRequest.of(0, 2000, Sort.by("receivedAt").descending());
        Page<Message> p = src == null
                ? messageRepository.findByMsgReadFalse(pageable)
                : messageRepository.findBySourceAndMsgReadFalse(src, pageable);
        if (p.getContent().isEmpty()) {
            return 0;
        }
        for (Message m : p.getContent()) {
            m.setMsgRead(true);
        }
        messageRepository.saveAll(p.getContent());
        return p.getContent().size();
    }

    @Transactional
    public void delete(Long id) {
        if (!messageRepository.existsById(id)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        messageRepository.deleteById(id);
    }
}
