package com.nexus.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SenderRuleRepository extends JpaRepository<SenderRule, Long> {

    List<SenderRule> findAllByOrderByCreatedAtAsc();

    Optional<SenderRule> findByPatternIgnoreCase(String pattern);
}
