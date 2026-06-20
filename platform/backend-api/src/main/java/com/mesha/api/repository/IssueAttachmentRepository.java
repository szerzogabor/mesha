package com.mesha.api.repository;

import com.mesha.api.model.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, UUID> {

    List<IssueAttachment> findAllByIssueIdOrderByCreatedAtAsc(UUID issueId);

    @Query("SELECT a FROM IssueAttachment a WHERE a.issue.id = :issueId ORDER BY a.createdAt ASC")
    List<IssueAttachment> findMetadataByIssueId(UUID issueId);
}
