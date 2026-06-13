package com.mesha.api.repository;

import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

    @Query("SELECT DISTINCT r FROM AutomationRule r LEFT JOIN FETCH r.actions a LEFT JOIN FETCH a.conditions "
            + "WHERE r.project.id = :projectId ORDER BY r.createdAt ASC")
    List<AutomationRule> findAllByProjectIdWithActions(@Param("projectId") UUID projectId);

    @Query("SELECT DISTINCT r FROM AutomationRule r LEFT JOIN FETCH r.actions a LEFT JOIN FETCH a.conditions "
            + "WHERE r.project.id = :projectId AND r.triggerType = :triggerType AND r.enabled = true "
            + "ORDER BY r.createdAt ASC")
    List<AutomationRule> findEnabledByProjectIdAndTriggerTypeWithActions(
            @Param("projectId") UUID projectId,
            @Param("triggerType") AutomationTriggerType triggerType);

    @Query("SELECT DISTINCT r FROM AutomationRule r LEFT JOIN FETCH r.actions a LEFT JOIN FETCH a.conditions "
            + "WHERE r.project.id = :projectId AND r.triggerType = :triggerType AND r.enabled = true "
            + "AND (r.triggerValue IS NULL OR r.triggerValue = :matchValue) "
            + "ORDER BY r.createdAt ASC")
    List<AutomationRule> findEnabledByProjectIdAndTriggerTypeAndValueWithActions(
            @Param("projectId") UUID projectId,
            @Param("triggerType") AutomationTriggerType triggerType,
            @Param("matchValue") String matchValue);
}
