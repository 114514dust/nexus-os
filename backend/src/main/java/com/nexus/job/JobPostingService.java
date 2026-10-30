package com.nexus.job;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.event.ChangeDomain;
import com.nexus.event.ChangeEventNotifier;
import com.nexus.setting.Setting;
import com.nexus.setting.SettingRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 校招岗位池服务。
 *
 * 与 {@link JobApplicationService} 的分工：这里管「外面的机会」，那边管「我的投递」。
 * 两者唯一的交汇点是 {@link #apply}：把一条岗位变成一条投递，并回填 jobId。
 *
 * 三个刻意的取舍：
 * 1. **去重靠来源 id**，不靠「公司 + 岗位名」—— 公告标题会改（补录、加岗位方向），
 *    靠内容去重会重复入库；
 * 2. **重复抓到时只更新易变字段**，不动 starred / ignored / jobId /
 *    createdAt，否则用户收藏过的岗位会在下次抓取后「变回新岗位」；
 * 3. **「新岗位」用入库时间判断，不用发布日期**：站点上大量岗位一直是「10/30」，
 *    用发布日期的话每天都显得有一堆新的，NEW 标就失去意义了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingService {

    private static final String KEY_FOCUS = "job.focusKeywords";
    private static final String KEY_AUTO_FETCH = "job.autoFetch";
    private static final String KEY_PAGES = "job.fetchPages";

    private static final String SCOPE_PENDING = "pending";
    private static final String SCOPE_APPLIED = "applied";
    private static final String SCOPE_IGNORED = "ignored";
    private static final String SCOPE_ALL = "all";

    /** NEW 标的时间窗口 */
    private static final int FRESH_DAYS = 3;

    private final JobPostingRepository repository;
    private final JobPostingFetcher fetcher;
    private final JobApplicationService jobApplicationService;
    private final JobApplicationRepository jobRepository;
    private final SettingRepository settingRepository;
    private final ChangeEventNotifier notifier;

    // ---- 查询 ----

    @Transactional(readOnly = true)
    public List<PostingView> list(String keyword, String category, String scope, boolean onlyMatched) {
        List<String> focus = focusKeywords();
        List<JobPosting> items = new ArrayList<>(repository.findAll());

        String sc = scope == null || scope.isBlank() ? SCOPE_PENDING : scope;
        switch (sc) {
            case SCOPE_APPLIED:
                items = items.stream().filter(p -> p.getJobId() != null).collect(Collectors.toList());
                break;
            case SCOPE_IGNORED:
                items = items.stream().filter(JobPosting::isIgnored).collect(Collectors.toList());
                break;
            case SCOPE_ALL:
                break;
            default:
                items = items.stream()
                        .filter(p -> !p.isIgnored() && p.getJobId() == null)
                        .collect(Collectors.toList());
                break;
        }

        if (category != null && !category.isBlank() && !"all".equals(category)) {
            items = items.stream().filter(p -> category.equals(p.getCategory())).collect(Collectors.toList());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            items = items.stream()
                    .filter(p -> contains(p.getCompany(), kw)
                            || contains(p.getTitle(), kw)
                            || contains(p.getPositions(), kw)
                            || contains(p.getCity(), kw))
                    .collect(Collectors.toList());
        }

        List<PostingView> views = items.stream()
                .map(p -> view(p, focus, sc))
                .collect(Collectors.toList());

        if (onlyMatched) {
            views = views.stream().filter(PostingView::isMatched).collect(Collectors.toList());
        }

        // 收藏 → 命中关注词 → 新岗位 → 发布日期倒序
        // （排序放服务端是因为「新」「匹配」都是服务端算的派生值）
        views.sort(Comparator
                .comparing(PostingView::isStarred).reversed()
                .thenComparing(PostingView::isMatched, Comparator.reverseOrder())
                .thenComparing(PostingView::isFresh, Comparator.reverseOrder())
                .thenComparing(v -> v.getPublishedAt() == null ? LocalDate.MIN : v.getPublishedAt(),
                        Comparator.reverseOrder())
                .thenComparing(PostingView::getId, Comparator.reverseOrder()));
        return views;
    }

    @Transactional(readOnly = true)
    public PostingStats stats() {
        List<JobPosting> all = repository.findAll();
        PostingStats s = new PostingStats();
        LocalDateTime freshLine = LocalDateTime.now().minusDays(FRESH_DAYS);
        s.setTotal(all.stream().filter(p -> !p.isIgnored()).count());
        s.setPending(all.stream().filter(p -> !p.isIgnored() && p.getJobId() == null).count());
        s.setApplied(all.stream().filter(p -> p.getJobId() != null).count());
        s.setFresh(all.stream()
                .filter(p -> !p.isIgnored() && p.getJobId() == null
                        && p.getCreatedAt() != null && p.getCreatedAt().isAfter(freshLine))
                .count());
        s.setStarred(all.stream().filter(JobPosting::isStarred).count());
        s.setSourceLabel("秋招网");
        s.setLastFetchedAt(all.stream()
                .map(JobPosting::getFetchedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        return s;
    }

    // ---- 抓取 ----

    /** 抓取结果，给前端/日志一个明确的交代 */
    @Data
    public static class RefreshResult {
        private boolean ok;
        private int added;
        private int updated;
        private int fetched;
        private String message;
        private LocalDateTime at;
        /**
         * 本次真正新增的 id。
         *
         * 推送不能拿列表里的 fresh 字段代替：fresh 是「近 3 天入库」，
         * 首次抓取时全库都是 fresh，推送就会变成「新增 60 个岗位」这种假消息。
         */
        private List<Long> newIds = new ArrayList<>();
    }

    /**
     * 抓取并入库。定时任务与手动刷新共用这一个入口。
     * 抓取失败（网络、改版）返回 ok=false 而不是抛异常 —— 调用方是定时任务。
     */
    @Transactional
    public RefreshResult refresh() {
        RefreshResult result = new RefreshResult();
        PostingPrefs prefs = getPrefs();
        List<JobPostingParser.Parsed> fetched = fetcher.fetch(prefs.getPages());
        result.setFetched(fetched.size());
        result.setAt(LocalDateTime.now());
        if (fetched.isEmpty()) {
            result.setOk(false);
            result.setMessage("本次没有抓到岗位（源站无响应或页面结构变化）");
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        int updated = 0;
        for (JobPostingParser.Parsed p : fetched) {
            Optional<JobPosting> existing =
                    repository.findBySourceAndSourceId(JobPostingParser.SOURCE, p.sourceId);
            if (existing.isPresent()) {
                JobPosting e = existing.get();
                // 只更新会变的信息，用户状态（收藏/忽略/已加入）一律保留
                applyParsed(e, p, now);
                repository.save(e);
                updated++;
            } else {
                JobPosting created = repository.save(fromParsed(p, now));
                result.getNewIds().add(created.getId());
                added++;
            }
        }
        result.setOk(true);
        result.setAdded(added);
        result.setUpdated(updated);
        result.setMessage(added > 0 ? "新增 " + added + " 个岗位" : "没有新岗位");
        log.info("[posting] 抓取完成：拉取 {} 条，新增 {}，更新 {}", fetched.size(), added, updated);
        if (added > 0 || updated > 0) {
            notifier.notify(ChangeDomain.POSTING);
        }
        return result;
    }

    private JobPosting fromParsed(JobPostingParser.Parsed p, LocalDateTime now) {
        JobPosting e = new JobPosting();
        e.setSource(JobPostingParser.SOURCE);
        e.setSourceId(p.sourceId);
        e.setStarred(false);
        e.setIgnored(false);
        e.setCreatedAt(now);
        applyParsed(e, p, now);
        return e;
    }

    /**
     * 把抓取结果写进实体。字段长度统一在这里收敛：
     * 源站有几个岗位的「岗位方向」长到 300+ 字（把整个招聘专业目录贴进去了），
     * 不截断会直接违反列长度约束、导致整批入库失败。
     */
    private void applyParsed(JobPosting e, JobPostingParser.Parsed p, LocalDateTime now) {
        e.setCompany(cut(p.company, 128));
        e.setTitle(nullToEmpty(cut(p.title, 250)));
        e.setPositions(cut(p.positions, 2000));
        e.setCity(cut(p.city, 256));
        e.setCohort(cut(p.cohort, 32));
        e.setBatch(cut(p.batch, 32));
        e.setCompanyType(cut(p.companyType, 32));
        e.setDeadlineText(cut(p.deadlineText, 64));
        e.setAnnounceUrl(cut(p.announceUrl, 1000));
        e.setDetailUrl(cut(p.detailUrl, 500));
        if (p.publishedAt != null) {
            e.setPublishedAt(p.publishedAt);
        } else if (e.getPublishedAt() == null) {
            e.setPublishedAt(LocalDate.now());
        }
        e.setCategory(inferCategory(p.batch, p.cohort, p.companyType, p.title, p.positions));
        e.setFetchedAt(now);
    }

    /** 批次/性质/标题里出现的关键词决定投递类型 —— 用户在「加入追踪」时还能改 */
    static String inferCategory(String batch, String cohort, String companyType, String title, String positions) {
        String hay = (nullToEmpty(batch) + " " + nullToEmpty(cohort) + " "
                + nullToEmpty(title) + " " + nullToEmpty(positions)).toLowerCase(Locale.ROOT);
        if (hay.contains("春招") || hay.contains("春季")) {
            return JobCategory.SPRING;
        }
        if (hay.contains("实习")) {
            return JobCategory.INTERN;
        }
        if (hay.contains("秋招") || hay.contains("秋季")) {
            return JobCategory.AUTUMN;
        }
        if (hay.contains("社招")) {
            return JobCategory.SOCIAL;
        }
        if (companyType != null && companyType.contains("国企")) {
            return JobCategory.SOE;
        }
        return JobCategory.OTHER;
    }

    // ---- 写入 ----

    /** 手动添加（内推群、公众号、学院就业网这些抓不到的渠道） */
    @Transactional
    public PostingView create(PostingCreateRequest req) {
        String company = req.getCompany() == null ? "" : req.getCompany().trim();
        if (company.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "公司名称不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        JobPosting p = new JobPosting();
        p.setSource("manual");
        p.setSourceId("manual-" + System.currentTimeMillis());
        p.setCompany(company);
        p.setTitle(req.getTitle() == null || req.getTitle().isBlank() ? company + " 招聘" : req.getTitle().trim());
        p.setPositions(trimToNull(req.getPositions()));
        p.setCity(trimToNull(req.getCity()));
        p.setCohort(trimToNull(req.getCohort()));
        p.setAnnounceUrl(trimToNull(req.getUrl()));
        p.setDeadlineText(trimToNull(req.getDeadlineText()));
        p.setCategory(JobCategory.isValid(req.getCategory())
                ? req.getCategory()
                : inferCategory(null, req.getCohort(), null, req.getTitle(), req.getPositions()));
        p.setPublishedAt(now.toLocalDate());
        p.setFetchedAt(now);
        p.setCreatedAt(now);
        JobPosting saved = repository.save(p);
        notifier.notify(ChangeDomain.POSTING);
        return view(saved, focusKeywords(), SCOPE_PENDING);
    }

    /** 收藏 / 忽略 */
    @Transactional
    public PostingView update(Long id, Boolean starred, Boolean ignored) {
        JobPosting p = getOrThrow(id);
        if (starred != null) {
            p.setStarred(starred);
        }
        if (ignored != null) {
            p.setIgnored(ignored);
        }
        JobPosting saved = repository.save(p);
        notifier.notify(ChangeDomain.POSTING);
        return view(saved, focusKeywords(), SCOPE_PENDING);
    }

    @Transactional
    public void delete(Long id) {
        JobPosting p = getOrThrow(id);
        repository.delete(p);
        notifier.notify(ChangeDomain.POSTING);
    }

    // ---- 加入求职追踪 ----

    @Data
    public static class ApplyResult {
        private PostingView posting;
        private JobApplication job;
        /** false 表示这条岗位之前已经加入过，本次是幂等返回 */
        private boolean created;
    }

    /**
     * 把一条岗位变成一条投递记录。
     *
     * 幂等：已经加入过的直接返回原投递，避免用户连点两下出现两条重复记录。
     * 但如果那条投递已经被删掉（用户放弃了这家），允许重新加入。
     */
    @Transactional
    public ApplyResult apply(Long id, PostingApplyRequest req) {
        JobPosting p = getOrThrow(id);
        PostingApplyRequest body = req == null ? new PostingApplyRequest() : req;

        if (p.getJobId() != null) {
            Optional<JobApplication> exist = jobRepository.findById(p.getJobId());
            if (exist.isPresent()) {
                ApplyResult r = new ApplyResult();
                r.setPosting(view(p, focusKeywords(), SCOPE_PENDING));
                r.setJob(exist.get());
                r.setCreated(false);
                return r;
            }
            // 投递记录被删了，留着一个悬空的 jobId 会让界面显示「已加入」却点不开
            p.setJobId(null);
        }

        JobCreateRequest cr = new JobCreateRequest();
        cr.setCompany(p.getCompany());
        cr.setJobTitle(body.getJobTitle() != null && !body.getJobTitle().isBlank()
                ? body.getJobTitle().trim()
                : guessJobTitle(p.getPositions(), p.getTitle()));
        cr.setCategory(JobCategory.isValid(body.getCategory()) ? body.getCategory() : p.getCategory());
        cr.setStage(JobStage.APPLIED);
        cr.setUrl(p.getAnnounceUrl() != null && !p.getAnnounceUrl().isBlank() ? p.getAnnounceUrl() : p.getDetailUrl());
        // 截止时间能解析成日期就直接填进 nextAt：加入追踪后不用再手填一遍，
        // 到那天还会被求职提醒（今天/明天有安排）推一次。
        cr.setNextAt(parseDeadline(p.getDeadlineText()));
        cr.setNextAction(buildNextAction(p.getDeadlineText()));
        cr.setStarred(body.getStarred());
        cr.setNote(body.getNote() == null ? "" : body.getNote());
        cr.setFirstNote(originNote(p));

        JobApplication job = jobApplicationService.create(cr);

        p.setJobId(job.getId());
        repository.save(p);
        notifier.notify(ChangeDomain.POSTING);

        ApplyResult r = new ApplyResult();
        r.setPosting(view(p, focusKeywords(), SCOPE_PENDING));
        r.setJob(job);
        r.setCreated(true);
        return r;
    }

    /**
     * 从公告的岗位明细里挑一个方向作为投递的岗位名。
     * 「投资业务岗 · 非投资业务岗」→「投资业务岗」；挑不出来就用公告标题。
     */
    static String guessJobTitle(String positions, String fallback) {
        if (positions != null && !positions.isBlank()) {
            for (String seg : positions.split("[、,，·；;]")) {
                String s = seg.trim();
                if (!s.isEmpty() && s.length() <= 40) {
                    return s;
                }
            }
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * 截止文案 → 日期。
     * 站点上多数是「尽快投递」（无日期，返回 null），少数给了
     * 「2026-11-15」「11月15日」「11.15」这类，解析出来就能直接用于提醒。
     */
    static LocalDate parseDeadline(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        Matcher full = Pattern.compile("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})").matcher(t);
        try {
            if (full.find()) {
                return LocalDate.of(Integer.parseInt(full.group(1)),
                        Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
            }
            Matcher shortForm = Pattern.compile("(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})").matcher(t);
            if (shortForm.find()) {
                LocalDate today = LocalDate.now();
                LocalDate d = LocalDate.of(today.getYear(),
                        Integer.parseInt(shortForm.group(1)), Integer.parseInt(shortForm.group(2)));
                // 只说「1月5日」的公告通常指明年
                return d.isBefore(today.minusDays(30)) ? d.plusYears(1) : d;
            }
        } catch (Exception e) {
            // 文案里恰好有类似日期的数字但组不成合法日期（如 13 月），当作没有日期
            return null;
        }
        return null;
    }

    private String buildNextAction(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        String d = deadline.trim();
        if (d.contains("尽快")) {
            return "尽快投递";
        }
        if (d.contains("截止")) {
            return d.length() <= 256 ? d : d.substring(0, 256);
        }
        return "投递截止：" + d;
    }

    private String originNote(JobPosting p) {
        StringBuilder sb = new StringBuilder("来自「校招岗位」");
        if (p.getBatch() != null && !p.getBatch().isBlank()) {
            sb.append(" · ").append(p.getBatch());
        }
        if (p.getCohort() != null && !p.getCohort().isBlank()) {
            sb.append(" · ").append(p.getCohort());
        }
        if (p.getPositions() != null && !p.getPositions().isBlank()) {
            sb.append("；公告岗位：").append(p.getPositions());
        }
        return sb.length() > 512 ? sb.substring(0, 512) : sb.toString();
    }

    // ---- 偏好设置 ----

    @Transactional(readOnly = true)
    public PostingPrefs getPrefs() {
        PostingPrefs p = new PostingPrefs();
        p.setFocusKeywords(focusKeywords());
        String auto = getValue(KEY_AUTO_FETCH);
        p.setAutoFetch(auto.isEmpty() || Boolean.parseBoolean(auto));
        String pages = getValue(KEY_PAGES);
        p.setPages(parsePages(pages));
        return p;
    }

    @Transactional
    public PostingPrefs savePrefs(PostingPrefs dto) {
        if (dto.getFocusKeywords() != null) {
            List<String> cleaned = dto.getFocusKeywords().stream()
                    .filter(k -> k != null && !k.trim().isEmpty())
                    .map(k -> k.trim().replace(",", ""))
                    .distinct()
                    .limit(20)
                    .collect(Collectors.toList());
            setValue(KEY_FOCUS, String.join(",", cleaned));
        }
        setValue(KEY_AUTO_FETCH, String.valueOf(dto.isAutoFetch()));
        setValue(KEY_PAGES, String.valueOf(parsePages(String.valueOf(dto.getPages()))));
        return getPrefs();
    }

    @Transactional(readOnly = true)
    public List<String> focusKeywords() {
        String raw = getValue(KEY_FOCUS);
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ---- 内部 ----

    private PostingView view(JobPosting p, List<String> focus, String scope) {
        PostingView v = new PostingView();
        v.setId(p.getId());
        v.setSource(p.getSource());
        v.setCompany(p.getCompany());
        v.setTitle(p.getTitle());
        v.setPositions(p.getPositions());
        v.setCity(p.getCity());
        v.setCohort(p.getCohort());
        v.setBatch(p.getBatch());
        v.setCompanyType(p.getCompanyType());
        v.setCategory(p.getCategory());
        v.setCategoryLabel(JobCategory.label(p.getCategory()));
        v.setDetailUrl(p.getDetailUrl());
        v.setAnnounceUrl(p.getAnnounceUrl());
        v.setDeadlineText(p.getDeadlineText());
        v.setPublishedAt(p.getPublishedAt());
        v.setStarred(p.isStarred());
        v.setIgnored(p.isIgnored());
        v.setJobId(p.getJobId());
        v.setFetchedAt(p.getFetchedAt());

        LocalDateTime freshLine = LocalDateTime.now().minusDays(FRESH_DAYS);
        v.setFresh(!p.isIgnored() && p.getJobId() == null
                && p.getCreatedAt() != null && p.getCreatedAt().isAfter(freshLine));

        List<String> hit = matchedKeywords(p, focus);
        v.setMatchKeywords(hit);
        v.setMatched(!hit.isEmpty());
        return v;
    }

    private List<String> matchedKeywords(JobPosting p, List<String> focus) {
        if (focus == null || focus.isEmpty()) {
            return Collections.emptyList();
        }
        String hay = (nullToEmpty(p.getCompany()) + " " + nullToEmpty(p.getTitle()) + " "
                + nullToEmpty(p.getPositions()) + " " + nullToEmpty(p.getBatch()))
                .toLowerCase(Locale.ROOT);
        return focus.stream()
                .filter(k -> hay.contains(k.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private JobPosting getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "岗位不存在"));
    }

    private static boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 截断到列宽以内；空串归为 null，让「没这个信息」和「空字符串」在库里长得一样 */
    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static int parsePages(String raw) {
        try {
            int n = Integer.parseInt(raw == null ? "" : raw.trim());
            return Math.max(1, Math.min(n, 5));
        } catch (Exception e) {
            return 2;
        }
    }

    private String getValue(String key) {
        return settingRepository.findByKey(key).map(Setting::getValue).orElse("");
    }

    private void setValue(String key, String value) {
        Setting setting = settingRepository.findByKey(key).orElseGet(Setting::new);
        setting.setKey(key);
        setting.setValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }
}
