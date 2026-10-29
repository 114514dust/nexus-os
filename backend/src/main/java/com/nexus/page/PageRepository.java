package com.nexus.page;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 页面仓储。
 */
public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findAllByOrderBySortOrderAscIdAsc();
}
