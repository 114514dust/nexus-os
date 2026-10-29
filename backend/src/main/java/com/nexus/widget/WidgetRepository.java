package com.nexus.widget;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WidgetRepository extends JpaRepository<WidgetDefinition, Long> {
}
