package com.mesha.api.repository;

import com.mesha.api.model.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    @Query("""
           SELECT a FROM ActivityEvent a
           LEFT JOIN FETCH a.user
           WHERE a.issue.id = :issueId
           ORDER BY a.createdAt ASC
           """)
    List<ActivityEvent> findByIssueIdOrderByCreatedAtAsc(@Param("issueId") UUID issueId);
}
