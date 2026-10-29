package com.nexus.link;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 快捷链接仓储。
 */
public interface LinkRepository extends JpaRepository<Link, Long> {
}
