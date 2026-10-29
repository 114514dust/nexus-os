package com.nexus.layout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 布局仓储。
 */
public interface LayoutRepository extends JpaRepository<Layout, Long> {

    Optional<Layout> findByPageId(Long pageId);

    void deleteByPageId(Long pageId);
}
