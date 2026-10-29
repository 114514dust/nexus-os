package com.nexus.page;

import com.nexus.common.ApiException;
import com.nexus.common.ErrorCode;
import com.nexus.layout.Layout;
import com.nexus.layout.LayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 页面服务：管理多页面，并在创建页面时初始化空布局。
 */
@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepository;
    private final LayoutRepository layoutRepository;

    @Transactional(readOnly = true)
    public List<Page> list() {
        return pageRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional
    public Page create(PageRequest req) {
        Page page = new Page();
        page.setName(req.getName().trim());
        page.setSortOrder(nextSortOrder());
        page.setCreatedAt(LocalDateTime.now());
        page = pageRepository.save(page);

        // 新页面初始化空布局
        Layout layout = new Layout();
        layout.setPageId(page.getId());
        layout.setItemsJson("[]");
        layout.setUpdatedAt(LocalDateTime.now());
        layoutRepository.save(layout);
        return page;
    }

    @Transactional
    public Page rename(Long id, PageRequest req) {
        Page page = getPage(id);
        page.setName(req.getName().trim());
        return pageRepository.save(page);
    }

    @Transactional
    public void delete(Long id) {
        Page page = getPage(id);
        if (pageRepository.count() <= 1) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "至少保留一个页面");
        }
        layoutRepository.deleteByPageId(id);
        pageRepository.delete(page);
    }

    private Page getPage(Long id) {
        return pageRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "页面不存在"));
    }

    private int nextSortOrder() {
        List<Page> pages = pageRepository.findAllByOrderBySortOrderAscIdAsc();
        return pages.stream().mapToInt(p -> p.getSortOrder() == null ? 0 : p.getSortOrder())
                .max().orElse(0) + 1;
    }
}
