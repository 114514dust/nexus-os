package com.nexus.algorithm;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 高频算法题服务：CRUD + 内存过滤排序。
 *
 * 个人使用场景数据量小（几十到几百题），列表筛选/排序直接在内存做，
 * 避免引入复杂的 Specification 查询。
 */
@Service
@RequiredArgsConstructor
public class AlgorithmQuestionService {

    private static final List<String> DIFFICULTIES = List.of("easy", "medium", "hard");

    private final AlgorithmQuestionRepository repository;

    @Transactional(readOnly = true)
    public List<AlgorithmQuestion> list(String keyword, String difficulty, String sort) {
        List<AlgorithmQuestion> items = repository.findAll();

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            items = items.stream()
                    .filter(q -> q.getTitle().toLowerCase(Locale.ROOT).contains(kw))
                    .collect(Collectors.toList());
        }
        if (difficulty != null && !difficulty.isBlank() && !"all".equals(difficulty)) {
            items = items.stream()
                    .filter(q -> q.getDifficulty().equals(difficulty))
                    .collect(Collectors.toList());
        }

        Comparator<AlgorithmQuestion> cmp;
        switch (sort == null ? "" : sort) {
            case "star":
                // 星级降序；星级相同时按频率降序（tie-breaker 单独 reversed，避免整体反转）
                cmp = Comparator.comparing(AlgorithmQuestion::getStar).reversed()
                        .thenComparing(Comparator.comparing(AlgorithmQuestion::getFrequency).reversed());
                break;
            case "recent":
                cmp = Comparator.comparing(AlgorithmQuestion::getUpdatedAt).reversed();
                break;
            case "frequency":
            default:
                cmp = Comparator.comparing(AlgorithmQuestion::getFrequency).reversed();
                break;
        }
        return items.stream().sorted(cmp).collect(Collectors.toList());
    }

    @Transactional
    public AlgorithmQuestion create(AlgorithmCreateRequest req) {
        String difficulty = normalizeDifficulty(req.getDifficulty());
        AlgorithmQuestion q = new AlgorithmQuestion();
        q.setTitle(req.getTitle().trim());
        q.setDifficulty(difficulty);
        q.setLeetcodeUrl(req.getLeetcodeUrl().trim());
        q.setFrequency(req.getFrequency());
        q.setStar(0);
        q.setNote("");
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        return repository.save(q);
    }

    @Transactional
    public AlgorithmQuestion update(Long id, AlgorithmUpdateRequest req) {
        AlgorithmQuestion q = getOrThrow(id);
        if (req.getTitle() != null) {
            q.setTitle(req.getTitle().trim());
        }
        if (req.getDifficulty() != null) {
            q.setDifficulty(normalizeDifficulty(req.getDifficulty()));
        }
        if (req.getLeetcodeUrl() != null) {
            q.setLeetcodeUrl(req.getLeetcodeUrl().trim());
        }
        if (req.getFrequency() != null) {
            q.setFrequency(req.getFrequency());
        }
        if (req.getStar() != null) {
            int star = req.getStar();
            if (star < 0 || star > 5) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "星级需在 0~5 之间");
            }
            q.setStar(star);
        }
        if (req.getNote() != null) {
            q.setNote(req.getNote());
        }
        q.setUpdatedAt(LocalDateTime.now());
        return repository.save(q);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    private AlgorithmQuestion getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "算法题不存在"));
    }

    private String normalizeDifficulty(String difficulty) {
        String d = difficulty == null ? "medium" : difficulty.trim().toLowerCase(Locale.ROOT);
        if (!DIFFICULTIES.contains(d)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "难度需为 easy/medium/hard");
        }
        return d;
    }
}
