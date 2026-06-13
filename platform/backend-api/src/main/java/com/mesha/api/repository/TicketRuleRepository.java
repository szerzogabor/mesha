package com.mesha.api.repository;

import com.mesha.api.model.TicketRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketRuleRepository extends JpaRepository<TicketRule, UUID> {

    @Query("""
        SELECT DISTINCT r FROM TicketRule r
        LEFT JOIN FETCH r.conditions
        WHERE r.project.id = :projectId
        ORDER BY r.createdAt ASC
        """)
    List<TicketRule> findAllByProjectIdWithConditions(@Param("projectId") UUID projectId);

    @Query("""
        SELECT DISTINCT r FROM TicketRule r
        LEFT JOIN FETCH r.restrictions
        WHERE r.project.id = :projectId
        ORDER BY r.createdAt ASC
        """)
    List<TicketRule> findAllByProjectIdWithRestrictions(@Param("projectId") UUID projectId);

    @Query("""
        SELECT DISTINCT r FROM TicketRule r
        LEFT JOIN FETCH r.conditions
        WHERE r.project.id = :projectId AND r.enabled = true
        ORDER BY r.createdAt ASC
        """)
    List<TicketRule> findEnabledByProjectIdWithConditions(@Param("projectId") UUID projectId);

    @Query("""
        SELECT DISTINCT r FROM TicketRule r
        LEFT JOIN FETCH r.restrictions
        WHERE r.project.id = :projectId AND r.enabled = true
        ORDER BY r.createdAt ASC
        """)
    List<TicketRule> findEnabledByProjectIdWithRestrictions(@Param("projectId") UUID projectId);
}
