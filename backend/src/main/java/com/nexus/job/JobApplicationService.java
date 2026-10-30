package com.nexus.job;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 求职追踪服务。
 *
 * 几个刻意的取舍：
 * 1. **阶段线性推进** {@link #advance}：求职最高频的操作就是「我进下一轮了」，
 *    点一下比选下拉框快得多；需要跳阶段（比如直接挂了）走 update。
 * 2. **阶段变化即写时间线**：不是额外让用户去记，而是把变更本身当素材，
 *    几个月后回看能看到自己每一家的节奏。
 * 3. **统计在服务端算**：前端的列表带筛选，只能看到局部的数据，
 *    统计必须基于全量，所以单独出 /stats 接口。
 * 4. 与其它组件一致，所有写操作都发 {@link ChangeDomain#JOB} 事件，
 *    前端组件（以及开了 AI 对话的分页）靠它自动刷新。
 */
@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private static final String SCOPE_ACTIVE = "active";
    private static final String SCOPE_CLOSED = "closed";

    private final JobApplicationRepository repository;
    private final JobEventRepository eventRepository;
    private final ChangeEventNotifier notifier;

    // ---- 查询 ----

    @Transactional(readOnly = true)
    public List<JobApplication> list(String keyword, String category, String stage, String scope, String sort) {
        List<JobApplication> items = new ArrayList<>(repository.findAll());

        String sc = scope == null || scope.isBlank() ? SCOPE_ACTIVE : scope;
        if (SCOPE_ACTIVE.equals(sc)) {
            items = items.stream().filter(j -> !JobStage.isClosed(j.getStage())).collect(Collectors.toList());
        } else if (SCOPE_CLOSED.equals(sc)) {
            items = items.stream().filter(j -> JobStage.isClosed(j.getStage())).collect(Collectors.toList());
        }

        if (category != null && !category.isBlank() && !"all".equals(category)) {
            items = items.stream().filter(j -> category.equals(j.getCategory())).collect(Collectors.toList());
        }
        if (stage != null && !stage.isBlank() && !"all".equals(stage)) {
            items = items.stream().filter(j -> stage.equals(j.getStage())).collect(Collectors.toList());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            items = items.stream()
                    .filter(j -> contains(j.getCompany(), kw)
                            || contains(j.getJobTitle(), kw)
                            || contains(j.getNextAction(), kw)
                            || contains(j.getNote(), kw))
                    .collect(Collectors.toList());
        }

        Comparator<JobApplication> cmp;
        switch (sort == null ? "" : sort) {
            case "due":
                // 有日期的排前面、日期早的优先；没填日期的沉底
                cmp = Comparator.comparing(JobApplication::getNextAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "company":
                cmp = Comparator.comparing((JobApplication j) -> j.getCompany() == null ? "" : j.getCompany());
                break;
            case "recent":
            default:
                cmp = Comparator.comparing(JobApplication::getUpdatedAt).reversed();
                break;
        }
        // 重点关注永远置顶，其次才按用户选的规则排
        Comparator<JobApplication> starredFirst =
                Comparator.<JobApplication, Boolean>comparing(JobApplication::isStarred).reversed();
        return items.stream().sorted(starredFirst.thenComparing(cmp)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobDetail detail(Long id) {
        JobApplication job = getOrThrow(id);
        return new JobDetail(job, eventRepository.findByJobIdOrderByOccurredAtDesc(id));
    }

    @Transactional(readOnly = true)
    public JobStats stats() {
        List<JobApplication> all = repository.findAll();
        JobStats s = new JobStats();
        s.setTotal(all.size());
        s.setInterviewing((int) all.stream().filter(j -> JobStage.isInterviewing(j.getStage())).count());
        s.setOffers((int) all.stream()
                .filter(j -> JobStage.OFFER.equals(j.getStage()) || JobStage.ACCEPTED.equals(j.getStage()))
                .count());
        s.setRejected((int) all.stream().filter(j -> JobStage.REJECTED.equals(j.getStage())).count());
        int closed = (int) all.stream().filter(j -> JobStage.isClosed(j.getStage())).count();
        s.setActive(all.size() - closed);

        int replied = (int) all.stream().filter(j -> JobStage.hasProgress(j.getStage())).count();
        s.setReplied(replied);
        s.setReplyRate(all.isEmpty() ? 0 : Math.round(replied * 100f / all.size()));

        LocalDate today = LocalDate.now();
        LocalDate weekLater = today.plusDays(7);
        s.setUpcoming((int) all.stream()
                .filter(j -> j.getNextAt() != null
                        && !JobStage.isClosed(j.getStage())
                        && !j.getNextAt().isBefore(today)
                        && !j.getNextAt().isAfter(weekLater))
                .count());
        return s;
    }

    // ---- 写入 ----

    @Transactional
    public JobApplication create(JobCreateRequest req) {
        String company = req.getCompany() == null ? "" : req.getCompany().trim();
        if (company.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "公司名称不能为空");
        }
        JobApplication job = new JobApplication();
        job.setCompany(company);
        job.setJobTitle(trimToEmpty(req.getJobTitle()));
        job.setCategory(normalizeCategory(req.getCategory()));
        job.setStage(normalizeStage(req.getStage(), JobStage.APPLIED));
        job.setUrl(trimToNull(req.getUrl()));
        job.setNextAction(trimToNull(req.getNextAction()));
        job.setNextAt(req.getNextAt());
        job.setStarred(Boolean.TRUE.equals(req.getStarred()));
        job.setNote(req.getNote() == null ? "" : req.getNote());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        JobApplication saved = repository.save(job);

        // 第一条时间线 = 建档，备注可选（内推 / 官网 / Boss 直聘…）
        saveEvent(saved.getId(), saved.getStage(), trimToNull(req.getFirstNote()));
        notifier.notify(ChangeDomain.JOB);
        return saved;
    }

    @Transactional
    public JobApplication update(Long id, JobUpdateRequest req) {
        JobApplication job = getOrThrow(id);
        if (req.getCompany() != null) {
            String company = req.getCompany().trim();
            if (company.isEmpty()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "公司名称不能为空");
            }
            job.setCompany(company);
        }
        if (req.getJobTitle() != null) {
            job.setJobTitle(req.getJobTitle().trim());
        }
        if (req.getCategory() != null) {
            job.setCategory(normalizeCategory(req.getCategory()));
        }
        if (req.getUrl() != null) {
            job.setUrl(trimToNull(req.getUrl()));
        }
        if (req.getNextAction() != null) {
            job.setNextAction(trimToNull(req.getNextAction()));
        }
        if (Boolean.TRUE.equals(req.getClearNextAt())) {
            job.setNextAt(null);
        } else if (req.getNextAt() != null) {
            job.setNextAt(req.getNextAt());
        }
        if (req.getStarred() != null) {
            job.setStarred(req.getStarred());
        }
        if (req.getNote() != null) {
            job.setNote(req.getNote());
        }

        boolean stageChanged = false;
        if (req.getStage() != null && !req.getStage().equals(job.getStage())) {
            job.setStage(normalizeStage(req.getStage(), job.getStage()));
            stageChanged = true;
        }

        job.setUpdatedAt(LocalDateTime.now());
        JobApplication saved = repository.save(job);
        if (stageChanged) {
            saveEvent(saved.getId(), saved.getStage(), trimToNull(req.getStageNote()));
        }
        notifier.notify(ChangeDomain.JOB);
        return saved;
    }

    /**
     * 推进到下一阶段。已到线性终点（已入职 / 未通过）时不做任何事，返回当前状态。
     */
    @Transactional
    public JobApplication advance(Long id, String note) {
        JobApplication job = getOrThrow(id);
        String next = JobStage.next(job.getStage());
        if (next == null) {
            return job;
        }
        job.setStage(next);
        job.setUpdatedAt(LocalDateTime.now());
        JobApplication saved = repository.save(job);
        saveEvent(saved.getId(), next, trimToNull(note));
        notifier.notify(ChangeDomain.JOB);
        return saved;
    }

    /** 追加一条纯备注（不推进阶段），用于记面试感受、HR 反馈等 */
    @Transactional
    public JobDetail addNote(Long id, String note) {
        JobApplication job = getOrThrow(id);
        if (note == null || note.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "备注内容不能为空");
        }
        saveEvent(job.getId(), job.getStage(), note.trim());
        // 只加备注也算一次「有动静」，刷新 updatedAt 以便跟进提醒重新计时
        job.setUpdatedAt(LocalDateTime.now());
        repository.save(job);
        notifier.notify(ChangeDomain.JOB);
        return new JobDetail(job, eventRepository.findByJobIdOrderByOccurredAtDesc(id));
    }

    @Transactional
    public void delete(Long id) {
        JobApplication job = getOrThrow(id);
        // 先删子表：DB 层虽然配了 ON DELETE CASCADE，但 H2 等方言并不总生效，
        // 显式删除能保证任何库上都不会留下孤儿时间线
        eventRepository.deleteByJobId(job.getId());
        repository.delete(job);
        notifier.notify(ChangeDomain.JOB);
    }

    // ---- 内部 ----

    private void saveEvent(Long jobId, String stage, String note) {
        JobEvent e = new JobEvent();
        e.setJobId(jobId);
        e.setStage(stage);
        e.setNote(note);
        e.setOccurredAt(LocalDateTime.now());
        eventRepository.save(e);
    }

    private JobApplication getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "投递记录不存在"));
    }

    private String normalizeCategory(String category) {
        String c = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) {
            return JobCategory.AUTUMN;
        }
        if (!JobCategory.isValid(c)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "未知的投递类型：" + category);
        }
        return c;
    }

    private String normalizeStage(String stage, String fallback) {
        String s = stage == null ? "" : stage.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return fallback;
        }
        if (!JobStage.isValid(s)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "未知的进度阶段：" + stage);
        }
        return s;
    }

    private static boolean contains(String text, String lowerKeyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
