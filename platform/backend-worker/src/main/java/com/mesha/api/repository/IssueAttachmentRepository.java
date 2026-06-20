package com.mesha.api.repository;

import com.mesha.api.model.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, UUID> {

    List<IssueAttachment> findAllByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
