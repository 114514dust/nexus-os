package com.nexus.link;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 快捷链接服务。
 */
@Service
@RequiredArgsConstructor
public class LinkService {

    private final LinkRepository linkRepository;

    @Transactional(readOnly = true)
    public List<Link> list() {
        return linkRepository.findAll(Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()));
    }

    @Transactional
    public Link create(LinkCreateRequest req) {
        Link link = new Link();
        link.setTitle(req.getTitle());
        link.setUrl(req.getUrl());
        link.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : (int) linkRepository.count());
        link.setCreatedAt(LocalDateTime.now());
        return linkRepository.save(link);
    }

    @Transactional
    public Link update(Long id, LinkUpdateRequest req) {
        Link link = getOrThrow(id);
        if (req.getTitle() != null) {
            link.setTitle(req.getTitle());
        }
        if (req.getUrl() != null) {
            link.setUrl(req.getUrl());
        }
        if (req.getSortOrder() != null) {
            link.setSortOrder(req.getSortOrder());
        }
        return link;
    }

    @Transactional
    public void delete(Long id) {
        Link link = getOrThrow(id);
        linkRepository.delete(link);
    }

    private Link getOrThrow(Long id) {
        return linkRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "链接不存在"));
    }
}
