package com.mesha.api.repository;

import com.mesha.api.model.IssueLink;
import com.mesha.api.model.IssueLinkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IssueLinkRepository extends JpaRepository<IssueLink, UUID> {

    @Query("""
        SELECT l FROM IssueLink l
        JOIN FETCH l.sourceIssue si
        JOIN FETCH si.project sp
        JOIN FETCH l.targetIssue ti
        JOIN FETCH ti.project tp
        WHERE si.id = :issueId OR ti.id = :issueId
        ORDER BY l.createdAt ASC
        """)
    List<IssueLink> findAllByIssueId(@Param("issueId") UUID issueId);

    boolean existsBySourceIssueIdAndTargetIssueIdAndLinkType(UUID sourceId, UUID targetId, IssueLinkType linkType);
}
