package com.mesha.worker.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface CommentWorkerRepository extends JpaRepository<CommentWorkerRecord, UUID> {

    @Query("SELECT c FROM CommentWorkerRecord c LEFT JOIN FETCH c.author WHERE c.issueId = :issueId ORDER BY c.createdAt ASC")
    List<CommentWorkerRecord> findByIssueIdOrderByCreatedAt(@Param("issueId") UUID issueId);
}
