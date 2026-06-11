package com.mesha.api.repository;

import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

    List<AutomationRule> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    List<AutomationRule> findAllByProjectIdAndTriggerTypeAndEnabledTrue(UUID projectId, AutomationTriggerType triggerType);
}
