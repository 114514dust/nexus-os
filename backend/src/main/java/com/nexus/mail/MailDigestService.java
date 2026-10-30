package com.nexus.mail;

import com.nexus.notify.NotifyService;
import com.nexus.todo.Todo;
import com.nexus.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 每日邮件摘要：早上把「昨天要紧的那几封」压成一条推送。
 *
 * 为什么还要这一层（明明昨天已经实时推过重要邮件了）：
 * 实时推送只解决「马上要处理的事」，但真实的早晨是——昨晚 22:00 之后到的邮件
 * 你根本没看，而在群里刷到十几条「重要邮件提醒」之后，人是不会再往上翻的。
 * 一条按天聚合的摘要，才是「昨天到底发生了什么」的完整答案。
 *
 * 两个刻意的取舍：
 * 1. **没有要紧邮件就不推**。「昨天 12 封，都不重要」这种消息第一次看是安心，
 *    第三天开始就是纯噪音；不推，本身就是「今天不用担心邮箱」的信号。
 * 2. **同一天只推一次**。定时任务重启、手动触发都不该让用户收到第二封摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailDigestService {

    /** 摘要里最多列几封邮件 */
    private static final int MAX_ITEMS = 8;

    /** 摘要整体长度上限，超出截断 —— 推送渠道对长文本的处理都不好看 */
    private static final int MAX_TEXT = 900;

    private final MailMessageRepository mailRepository;
    private final TodoRepository todoRepository;
    private final MailAttachmentRepository attachmentRepository;
    private final MailSettingsService settingsService;
    private final NotifyService notifyService;

    /** 汇总某一天（按收到时间），不推送 */
    @Transactional(readOnly = true)
    public MailDigest build(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        List<MailMessage> mails = mailRepository
                .findByReceivedAtGreaterThanEqualAndReceivedAtLessThanOrderByReceivedAtAsc(
                        day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        MailDigest d = new MailDigest();
        d.setDate(day.toString());
        d.setTotal(mails.size());
        d.setHigh((int) mails.stream().filter(m -> ImportanceClassifier.HIGH.equals(m.getImportantLevel())).count());
        d.setMedium((int) mails.stream()
                .filter(m -> ImportanceClassifier.MEDIUM.equals(m.getImportantLevel())).count());
        d.setLow(d.getTotal() - d.getHigh() - d.getMedium());
        d.setTodos((int) mails.stream().filter(m -> m.getTodoId() != null).count());

        List<MailMessage> important = mails.stream()
                .filter(m -> !ImportanceClassifier.LOW.equals(m.getImportantLevel()))
                .filter(m -> !m.isIgnored())
                // 重要的排前面，同级按时间
                .sorted(Comparator
                        .comparingInt((MailMessage m) -> ImportanceClassifier.HIGH.equals(m.getImportantLevel()) ? 0 : 1)
                        .thenComparing(MailMessage::getReceivedAt))
                .collect(Collectors.toList());

        List<Long> ids = important.stream().map(MailMessage::getId).collect(Collectors.toList());
        Map<Long, List<String>> attachments = attachmentNames(ids);
        Map<Long, Todo> todos = todoMap(mails);

        for (MailMessage m : important) {
            MailDigest.Item item = new MailDigest.Item();
            item.setId(m.getId());
            item.setSubject(m.getSubject());
            item.setSenderName(senderOf(m));
            item.setReceivedAt(m.getReceivedAt() == null ? "" : m.getReceivedAt().toString());
            item.setLevel(m.getImportantLevel());
            item.setScore(m.getImportantScore());
            item.setHasAttachment(m.isHasAttachment());
            item.setAttachments(attachments.getOrDefault(m.getId(), List.of()));
            Todo todo = m.getTodoId() == null ? null : todos.get(m.getTodoId());
            item.setTodoContent(todo == null ? null : todo.getContent());
            d.getItems().add(item);
        }

        d.setDueToday(dueToday(mails));
        d.setTopSenders(topSenders(mails));
        d.setText(render(d));
        return d;
    }

    /**
     * 推送某一天的摘要。没有要紧内容时返回 false（不打扰）。
     * 手动触发也走这里 —— 用户既然点了，就给他一个明确结果。
     */
    public boolean push(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        MailDigest d = build(day);
        if (d.getHigh() + d.getMedium() == 0 && d.getDueToday().isEmpty()) {
            settingsService.markDigest(day);
            return false;
        }
        notifyService.notifyAsync(NotifyService.EVENT_MAIL_DIGEST, title(d), d.getText());
        settingsService.markDigest(day);
        log.info("[mail] 邮件摘要已推送：{}（重要 {}，可关注 {}）", d.getDate(), d.getHigh(), d.getMedium());
        return true;
    }

    /** 定时任务用：今天已经推过就不再推，重启也不会补发第二封 */
    public boolean pushIfAbsent(LocalDate date) {
        String last = settingsService.load().getLastDigestDate();
        if (last != null && last.trim().equals(date.toString())) {
            log.debug("[mail] {} 的摘要已推送过，跳过", date);
            return false;
        }
        return push(date);
    }

    private static String title(MailDigest d) {
        return "📮 邮件摘要 · " + cnDate(d.getDate());
    }

    private String render(MailDigest d) {
        StringBuilder sb = new StringBuilder();
        sb.append(cnDate(d.getDate())).append("共收到 ").append(d.getTotal()).append(" 封");
        if (d.getHigh() + d.getMedium() > 0) {
            sb.append("：重要 ").append(d.getHigh());
            if (d.getMedium() > 0) {
                sb.append(" · 可关注 ").append(d.getMedium());
            }
        } else {
            sb.append("，没有要紧的");
        }
        sb.append("\n");

        List<MailDigest.Item> show = d.getItems().stream().limit(MAX_ITEMS).collect(Collectors.toList());
        for (MailDigest.Item item : show) {
            sb.append("· ");
            if (ImportanceClassifier.HIGH.equals(item.getLevel())) {
                sb.append("🔴");
            }
            sb.append(cut(item.getSenderName(), 12)).append("：").append(cut(item.getSubject(), 40));
            if (!item.getAttachments().isEmpty()) {
                sb.append(" 📎").append(cut(item.getAttachments().get(0), 18));
            }
            sb.append("\n");
        }
        if (d.getItems().size() > show.size()) {
            sb.append("…另有 ").append(d.getItems().size() - show.size()).append(" 封要紧邮件\n");
        }
        if (d.getLow() > 0) {
            sb.append("其余 ").append(d.getLow()).append(" 封为普通邮件，未列出\n");
        }
        if (d.getTodos() > 0) {
            sb.append("已从中转成 ").append(d.getTodos()).append(" 条待办\n");
        }
        if (!d.getDueToday().isEmpty()) {
            sb.append("今天到期：")
                    .append(d.getDueToday().stream().map(s -> cut(s, 20)).collect(Collectors.joining("、")))
                    .append("\n");
        }
        String text = sb.toString().trim();
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT) + "…";
    }

    /** 今天到期、且由邮件生成的待办 */
    private List<String> dueToday(List<MailMessage> mails) {
        LocalDate today = LocalDate.now();
        List<Long> todoIds = mails.stream()
                .map(MailMessage::getTodoId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (todoIds.isEmpty()) {
            return new ArrayList<>();
        }
        return todoRepository.findAllById(todoIds).stream()
                .filter(t -> !t.isDone())
                .filter(t -> today.equals(t.getDueDate()))
                .map(Todo::getContent)
                .collect(Collectors.toList());
    }

    /**
     * 发件人分布。只列出现两次以上的 ——
     * 「今天有 4 封来自就业中心」是信息，「有 1 封来自某公司」不是。
     */
    private static List<String> topSenders(List<MailMessage> mails) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MailMessage m : mails) {
            counts.merge(senderOf(m), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(e -> e.getKey() + " ×" + e.getValue())
                .collect(Collectors.toList());
    }

    private Map<Long, List<String>> attachmentNames(List<Long> mailIds) {
        if (mailIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (MailAttachment a : attachmentRepository.findByMailIdInOrderByMailIdAscPartIndexAsc(mailIds)) {
            map.computeIfAbsent(a.getMailId(), k -> new ArrayList<>()).add(a.getFilename());
        }
        return map;
    }

    private Map<Long, Todo> todoMap(List<MailMessage> mails) {
        List<Long> ids = mails.stream()
                .map(MailMessage::getTodoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return todoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Todo::getId, t -> t));
    }

    private static String senderOf(MailMessage m) {
        String name = m.getSenderName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String addr = m.getSenderAddr() == null ? "" : m.getSenderAddr();
        int at = addr.indexOf('@');
        return at > 0 ? addr.substring(0, at) : addr;
    }

    /** 2026-10-29 → 10月29日 */
    private static String cnDate(String isoDate) {
        try {
            LocalDate d = LocalDate.parse(isoDate);
            return d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
        } catch (Exception e) {
            return isoDate;
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
