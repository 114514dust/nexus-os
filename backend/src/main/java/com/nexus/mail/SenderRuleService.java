package com.nexus.mail;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 发件人规则的读写与匹配。
 *
 * 匹配规则只有一条，但必须写清楚：**pattern 越长越优先**。
 * 用户写「edu.cn → 重要」之后，又补一条「job@xx.edu.cn → 忽略」，
 * 他显然是想让后者覆盖前者；按「越具体越优先」理解，才符合人的直觉。
 * 长度相同时 ignore 优先 —— 用户明确说过不想看，就别再提醒。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SenderRuleService {

    private static final int PATTERN_MAX = 128;
    private static final int NOTE_MAX = 128;

    private final SenderRuleRepository repository;

    @Transactional(readOnly = true)
    public List<SenderRule> all() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public SenderRule create(String pattern, String action, String note) {
        String p = normalizePattern(pattern);
        // 唯一约束会把重复 pattern 变成 500，用户只会看到「服务器内部错误」；
        // 提前拦下来，才能告诉他「这条已经有了，改动作就行」
        repository.findByPatternIgnoreCase(p).ifPresent(existing -> {
            throw new ApiException(ErrorCode.CONFLICT,
                    "规则「" + existing.getPattern() + "」已经存在，直接改它的动作就好");
        });
        SenderRule rule = new SenderRule();
        rule.setPattern(p);
        rule.setAction(normalizeAction(action));
        rule.setNote(cutNote(note));
        rule.setCreatedAt(LocalDateTime.now());
        rule.setHitCount(0);
        return repository.save(rule);
    }

    @Transactional
    public SenderRule update(Long id, String pattern, String action, String note) {
        SenderRule rule = getOrThrow(id);
        if (pattern != null && !pattern.trim().isEmpty()) {
            String p = normalizePattern(pattern);
            repository.findByPatternIgnoreCase(p)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ApiException(ErrorCode.CONFLICT,
                                "规则「" + other.getPattern() + "」已经存在");
                    });
            rule.setPattern(p);
        }
        if (action != null && !action.trim().isEmpty()) {
            rule.setAction(normalizeAction(action));
        }
        rule.setNote(cutNote(note));
        return repository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    /**
     * 记一次命中。刻意不抛异常：统计数字不准是小事，
     * 因为它导致一封邮件入库失败才是大事。
     */
    @Transactional
    public void bumpHit(Long id) {
        try {
            repository.findById(id).ifPresent(rule -> {
                rule.setHitCount(rule.getHitCount() + 1);
                rule.setLastHitAt(LocalDateTime.now());
                repository.save(rule);
            });
        } catch (Exception e) {
            log.debug("[mail] 规则命中计数失败 id={}：{}", id, e.getMessage());
        }
    }

    /** 命中哪条规则（没有则 null）。列表由调用方一次查出、逐封复用 */
    public static SenderRule match(List<SenderRule> rules, String addr, String name) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        SenderRule best = null;
        for (SenderRule rule : rules) {
            if (!rule.matches(addr, name)) {
                continue;
            }
            if (best == null) {
                best = rule;
                continue;
            }
            int len = patternLength(rule);
            int bestLen = patternLength(best);
            if (len > bestLen
                    || (len == bestLen && SenderRule.IGNORE.equals(rule.getAction())
                            && !SenderRule.IGNORE.equals(best.getAction()))) {
                best = rule;
            }
        }
        return best;
    }

    private static int patternLength(SenderRule rule) {
        return rule.getPattern() == null ? 0 : rule.getPattern().trim().length();
    }

    private SenderRule getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "规则不存在"));
    }

    /**
     * 归一化 pattern：去掉首尾空白、压成小写。
     * 保留原样的大小写没有意义 —— 匹配本来就是大小写不敏感的，
     * 存小写可以让「同一规则加两次」被唯一约束挡住。
     */
    private static String normalizePattern(String pattern) {
        String p = pattern == null ? "" : pattern.trim();
        if (p.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "规则内容不能为空");
        }
        if (p.length() > PATTERN_MAX) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "规则内容太长（最多 " + PATTERN_MAX + " 个字符）");
        }
        return p.toLowerCase(Locale.ROOT);
    }

    private static String normalizeAction(String action) {
        String a = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!SenderRule.isValidAction(a)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不支持的动作：" + action);
        }
        return a;
    }

    private static String cutNote(String note) {
        String n = note == null ? "" : note.trim().replaceAll("\\s+", " ");
        return n.length() <= NOTE_MAX ? n : n.substring(0, NOTE_MAX);
    }
}
