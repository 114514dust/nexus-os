package com.nexus.mail;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
import com.nexus.todo.Todo;
import com.nexus.todo.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮箱订阅服务：配置 + 收取 + 判定 + 转待办 + 查询。
 *
 * 几个刻意的边界：
 * 1. **sync 不加 @Transactional**。收信是网络 IO，可能耗时十几秒，
 *    把它包在一个数据库事务里会长时间占着连接；单封入库各自提交，
 *    中途失败也只丢那一封，不会整批回滚。
 * 2. **不写邮箱**。不标已读、不删除服务端邮件，所有状态只存在本地。
 * 3. **只对高置信度自动建待办**。中置信度留在列表里等用户点一下 ——
 *    待办列表被噪音塞满之后，用户就不看它了，那才是真正的失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final int LIST_LIMIT_MAX = 300;

    private static final int TODO_CONTENT_MAX = 200;

    /** 推送里最多列几条邮件标题 */
    private static final int HIGHLIGHT_MAX = 6;

    private final MailMessageRepository mailRepository;
    private final TodoRepository todoRepository;
    private final MailSettingsService settingsService;
    private final MailFetcher fetcher;
    private final ImportanceClassifier classifier;
    private final MailLlmClient llmClient;
    private final MailAttachmentService attachmentService;
    private final SenderRuleService ruleService;
    private final ChangeEventNotifier notifier;

    // ---- 配置 ----

    public MailSettingsDto settings() {
        return settingsService.view();
    }

    public MailSettingsDto saveSettings(MailSettingsDto dto) {
        return settingsService.save(dto);
    }

    /** 测试连接：只连一次、读一下收件箱总数，不做任何写入 */
    public MailSyncResult probe() {
        MailSettingsDto cfg = settingsService.load();
        if (!cfg.isConfigured()) {
            return MailSyncResult.fail("请先填写邮箱地址和 IMAP 授权码");
        }
        try {
            int total = fetcher.probe(cfg);
            MailSyncResult r = new MailSyncResult();
            r.setOk(true);
            r.setMessage("连接成功，「" + cfg.getFolder() + "」里共有 " + total + " 封邮件");
            r.setFetched(total);
            settingsService.markFailed("");
            return r;
        } catch (Exception e) {
            String reason = MailErrors.friendly(e);
            settingsService.markFailed(reason);
            return MailSyncResult.fail(reason);
        }
    }

    // ---- 收取 ----

    /**
     * 收一次信。定时任务与前端「立即收取」都走这里，行为完全一致。
     */
    public MailSyncResult sync() {
        MailSettingsDto cfg = settingsService.load();
        if (!cfg.isConfigured()) {
            return MailSyncResult.fail("请先填写邮箱地址和 IMAP 授权码");
        }

        Set<Long> known = new HashSet<>(mailRepository.findImapUidsByAccount(cfg.getAddress()));
        List<FetchedMail> fetched;
        try {
            fetched = fetcher.fetch(cfg, known);
        } catch (Exception e) {
            String reason = MailErrors.friendly(e);
            settingsService.markFailed(reason);
            log.warn("[mail] 收信失败：{}", reason);
            return MailSyncResult.fail(reason);
        }

        // 规则一次查出、整批复用：每封信查一次库没必要
        List<SenderRule> rules = ruleService.all();

        int high = 0;
        int medium = 0;
        int todos = 0;
        MailSyncResult r = new MailSyncResult();
        r.setOk(true);
        for (FetchedMail fm : fetched) {
            try {
                MailMessage saved = ingest(fm, cfg, rules);
                if (ImportanceClassifier.HIGH.equals(saved.getImportantLevel())) {
                    high++;
                } else if (ImportanceClassifier.MEDIUM.equals(saved.getImportantLevel())) {
                    medium++;
                }
                if (saved.getTodoId() != null) {
                    todos++;
                }
                if (!ImportanceClassifier.LOW.equals(saved.getImportantLevel())
                        && r.getHighlights().size() < HIGHLIGHT_MAX) {
                    r.getHighlights().add("· " + cut(saved.getSubject(), 40));
                }
            } catch (Exception e) {
                // 单封评分/入库失败不该影响其它邮件
                log.warn("[mail] 处理邮件失败 uid={} 主题={} 原因={}",
                        fm.getImapUid(), fm.getSubject(), e.getMessage());
            }
        }

        settingsService.markSynced(LocalDateTime.now());
        if (!fetched.isEmpty()) {
            notifier.notify(ChangeDomain.MAIL);
        }
        if (todos > 0) {
            notifier.notify(ChangeDomain.TODO);
        }

        r.setFetched(fetched.size());
        r.setHigh(high);
        r.setMedium(medium);
        r.setTodos(todos);
        r.setMessage(fetched.isEmpty()
                ? "没有新邮件"
                : "收到 " + fetched.size() + " 封新邮件，其中重要 " + (high + medium) + " 封"
                        + (todos > 0 ? "，已自动转 " + todos + " 条待办" : ""));
        log.info("[mail] 收信完成：新邮件 {} 封，高 {} / 中 {}，建待办 {}", fetched.size(), high, medium, todos);
        return r;
    }

    /** 判定 + 入库（+ 必要时建待办），返回落库后的实体 */
    private MailMessage ingest(FetchedMail fm, MailSettingsDto cfg, List<SenderRule> rules) {
        ImportanceClassifier.Verdict rule = classifier.classify(fm, cfg, rules);
        int score = rule.getScore();
        String reason = rule.getReason();
        LocalDate due = rule.getDueDate();
        String verdictBy = "rule";
        String llmTodo = null;

        boolean worthLlm = ImportanceClassifier.HIGH.equals(rule.getLevel())
                || ImportanceClassifier.inGrayZone(score);
        if (cfg.isLlmJudge() && worthLlm) {
            Optional<MailLlmClient.Verdict> maybe = llmClient.classify(fm, score, rule.getReason());
            if (maybe.isPresent()) {
                MailLlmClient.Verdict llm = maybe.get();
                verdictBy = "llm";
                if (llm.getReason() != null && !llm.getReason().isBlank()) {
                    reason = reason + " · AI：" + cut(llm.getReason(), 160);
                }
                if (llm.getTodo() != null) {
                    llmTodo = llm.getTodo();
                }
                LocalDate llmDue = MailLlmClient.parseDate(llm.getDueDate());
                if (llmDue != null) {
                    due = llmDue;
                }
                // 灰区只能被「抬上来」：LLM 说有行动就升到高置信度。
                // 但不允许它把已经高分的邮件压下去 —— 关键词是硬信号，
                // 模型一次误判漏掉面试通知的代价，远大于多建一条待办。
                if (llm.isImportant() && score < ImportanceClassifier.HIGH_THRESHOLD) {
                    score = Math.min(100, Math.max(ImportanceClassifier.HIGH_THRESHOLD, llm.getScore()));
                }
            }
        }

        MailMessage m = new MailMessage();
        m.setAccount(cfg.getAddress());
        m.setImapUid(fm.getImapUid());
        m.setMessageId(cut(fm.getMessageId(), 250));
        m.setSenderAddr(cut(fm.getSenderAddr(), 255));
        m.setSenderName(cut(fm.getSenderName(), 128));
        m.setSubject(cut(fm.getSubject(), 500));
        m.setSnippet(cut(fm.getSnippet(), 1000));
        m.setBody(fm.getBody());
        m.setSentAt(fm.getSentAt());
        m.setReceivedAt(fm.getReceivedAt() == null ? LocalDateTime.now() : fm.getReceivedAt());
        m.setImportantScore(score);
        m.setImportantLevel(ImportanceClassifier.levelOf(score));
        m.setScoreReason(cut(reason, 500));
        m.setVerdictBy(verdictBy);
        m.setHasAttachment(fm.isHasAttachment());
        m.setReadFlag(fm.isRead());
        m.setIgnored(false);
        m.setCreatedAt(LocalDateTime.now());
        MailMessage saved = mailRepository.save(m);

        // 附件只记元信息，字节等用户点下载时再取
        try {
            attachmentService.saveAll(saved.getId(), fm.getAttachments());
        } catch (Exception e) {
            log.warn("[mail] 保存附件元信息失败 邮件={} 原因={}", saved.getId(), e.getMessage());
        }
        if (rule.getRuleId() != null) {
            ruleService.bumpHit(rule.getRuleId());
        }

        if (ImportanceClassifier.HIGH.equals(saved.getImportantLevel()) && cfg.isAutoTodo()) {
            Todo todo = createTodo(saved.getSubject(), llmTodo, due);
            saved.setTodoId(todo.getId());
            saved = mailRepository.save(saved);
        }
        return saved;
    }

    private Todo createTodo(String subject, String llmTodo, LocalDate due) {
        Todo todo = new Todo();
        todo.setContent(todoContent(subject, llmTodo));
        todo.setDone(false);
        todo.setDueDate(due);
        todo.setCreatedAt(LocalDateTime.now());
        return todoRepository.save(todo);
    }

    private static String todoContent(String subject, String llmTodo) {
        String base = llmTodo != null && !llmTodo.trim().isEmpty() ? llmTodo : subject;
        String t = base == null ? "" : base.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            t = "处理一封重要邮件";
        }
        return t.length() <= TODO_CONTENT_MAX ? t : t.substring(0, TODO_CONTENT_MAX);
    }

    // ---- 查询 ----

    /**
     * @param scope   important（默认，去掉 low 与已忽略）/ all / todo（已转待办）
     * @param keyword 主题、发件人、摘要模糊匹配
     */
    @Transactional(readOnly = true)
    public List<MailView> list(String scope, String keyword, int limit) {
        String sc = scope == null || scope.trim().isEmpty() ? "important" : scope.trim();
        List<MailMessage> raw;
        if ("important".equals(sc)) {
            raw = mailRepository.findTop200ByIgnoredFalseAndImportantLevelNotOrderByReceivedAtDesc(
                    ImportanceClassifier.LOW);
        } else {
            raw = mailRepository.findTop500ByOrderByReceivedAtDesc();
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int max = limit <= 0 ? 100 : Math.min(limit, LIST_LIMIT_MAX);

        List<MailMessage> filtered = new ArrayList<>();
        for (MailMessage m : raw) {
            if (!"all".equals(sc) && m.isIgnored()) {
                continue;
            }
            if ("todo".equals(sc) && m.getTodoId() == null) {
                continue;
            }
            if (!kw.isEmpty() && !matches(m, kw)) {
                continue;
            }
            filtered.add(m);
            if (filtered.size() >= max) {
                break;
            }
        }
        return toViews(filtered);
    }

    @Transactional(readOnly = true)
    public MailView detail(Long id) {
        MailMessage m = getOrThrow(id);
        Map<Long, Todo> todos = todoMap(Collections.singletonList(m));
        MailView view = toView(m, todos.get(m.getTodoId()), attachmentService.listByMail(m.getId()));
        view.setBody(m.getBody());
        return view;
    }

    @Transactional(readOnly = true)
    public MailStats stats() {
        MailStats s = new MailStats();
        s.setHigh(mailRepository.countByIgnoredFalseAndImportantLevel(ImportanceClassifier.HIGH));
        s.setMedium(mailRepository.countByIgnoredFalseAndImportantLevel(ImportanceClassifier.MEDIUM));
        s.setTotal(mailRepository.countByIgnoredFalse());
        // 待办被删后 mail.todoId 会悬挂，直接 count 会把数字算虚 —— 按「待办真实存在」来数
        List<MailMessage> linked = mailRepository.findByTodoIdNotNull();
        if (!linked.isEmpty()) {
            Set<Long> ids = linked.stream().map(MailMessage::getTodoId).collect(Collectors.toSet());
            s.setToTodo(todoRepository.findAllById(ids).size());
        }
        MailSettingsDto cfg = settingsService.view();
        s.setLastSyncAt(cfg.getLastSyncAt());
        s.setLastError(cfg.getLastError());
        s.setConfigured(cfg.isConfigured());
        return s;
    }

    // ---- 操作 ----

    /** 手动把一封邮件转成待办（中/低置信度邮件的用法） */
    @Transactional
    public MailView toTodo(Long id) {
        MailMessage m = getOrThrow(id);
        Todo existing = m.getTodoId() == null ? null : todoRepository.findById(m.getTodoId()).orElse(null);
        if (existing == null) {
            LocalDate due = classifier.extractDueDate(m.getSubject() + "\n" + safe(m.getBody()), m.getReceivedAt());
            Todo todo = createTodo(m.getSubject(), null, due);
            m.setTodoId(todo.getId());
            m.setIgnored(false);
            mailRepository.save(m);
            notifier.notify(ChangeDomain.TODO);
            notifier.notify(ChangeDomain.MAIL);
        }
        MailView view = toView(m,
                existing != null ? existing : todoRepository.findById(m.getTodoId()).orElse(null),
                attachmentService.listByMail(m.getId()));
        view.setBody(m.getBody());
        return view;
    }

    @Transactional
    public void ignore(Long id) {
        MailMessage m = getOrThrow(id);
        m.setIgnored(true);
        mailRepository.save(m);
        notifier.notify(ChangeDomain.MAIL);
    }

    /**
     * 删除这条邮件记录。**不删待办**：用户要删待办会去待办组件里删，
     * 顺手删邮件不该连带删掉一条他已经看过的待办。
     */
    @Transactional
    public void delete(Long id) {
        MailMessage m = getOrThrow(id);
        attachmentService.deleteForMail(id);
        mailRepository.delete(m);
        notifier.notify(ChangeDomain.MAIL);
    }

    // ---- 视图装配 ----

    private List<MailView> toViews(List<MailMessage> list) {
        Map<Long, Todo> todos = todoMap(list);
        Map<Long, List<MailAttachmentView>> atts = attachmentService.mapByMails(
                list.stream().map(MailMessage::getId).collect(Collectors.toList()));
        return list.stream()
                .map(m -> toView(m, todos.get(m.getTodoId()), atts.getOrDefault(m.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private Map<Long, Todo> todoMap(List<MailMessage> list) {
        Set<Long> ids = list.stream()
                .map(MailMessage::getTodoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return todoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Todo::getId, t -> t));
    }

    private static MailView toView(MailMessage m, Todo todo, List<MailAttachmentView> attachments) {
        MailView v = new MailView();
        v.setId(m.getId());
        v.setSenderAddr(m.getSenderAddr());
        String name = m.getSenderName();
        if (name == null || name.trim().isEmpty()) {
            String addr = m.getSenderAddr() == null ? "" : m.getSenderAddr();
            int at = addr.indexOf('@');
            name = at > 0 ? addr.substring(0, at) : addr;
        }
        v.setSenderName(name);
        v.setSubject(m.getSubject());
        v.setSnippet(m.getSnippet());
        v.setReceivedAt(m.getReceivedAt());
        v.setImportantLevel(m.getImportantLevel());
        v.setImportantScore(m.getImportantScore());
        v.setScoreReason(m.getScoreReason());
        v.setVerdictBy(m.getVerdictBy());
        v.setHasAttachment(m.isHasAttachment());
        v.setAttachments(attachments == null ? Collections.emptyList() : attachments);
        v.setRead(m.isReadFlag());
        v.setIgnored(m.isIgnored());
        // 待办被用户删掉后这里会悬挂 —— 当作「没转过」，否则前端会显示一条不存在的待办
        if (todo != null) {
            v.setTodoId(todo.getId());
            v.setTodoExists(true);
            v.setTodoContent(todo.getContent());
            v.setTodoDueDate(todo.getDueDate() == null ? null : todo.getDueDate().toString());
        } else {
            v.setTodoId(null);
            v.setTodoExists(false);
        }
        return v;
    }

    private MailMessage getOrThrow(Long id) {
        return mailRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "邮件不存在"));
    }

    // ---- 工具 ----

    private static boolean matches(MailMessage m, String kw) {
        return contains(m.getSubject(), kw)
                || contains(m.getSenderAddr(), kw)
                || contains(m.getSenderName(), kw)
                || contains(m.getSnippet(), kw);
    }

    private static boolean contains(String s, String kw) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(kw);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
