package com.nexus.setting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 应用设置仓储。
 */
public interface SettingRepository extends JpaRepository<Setting, Long> {

    Optional<Setting> findByKey(String key);
}
