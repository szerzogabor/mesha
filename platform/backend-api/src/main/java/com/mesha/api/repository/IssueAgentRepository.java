package com.mesha.api.repository;

import com.mesha.api.model.IssueAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueAgentRepository extends JpaRepository<IssueAgent, UUID> {
    List<IssueAgent> findAllByIssueIdOrderByAssignedAtDesc(UUID issueId);
    Optional<IssueAgent> findByIssueIdAndAgentDefinitionId(UUID issueId, UUID agentDefinitionId);
    boolean existsByIssueIdAndAgentDefinitionId(UUID issueId, UUID agentDefinitionId);
    void deleteByIssueIdAndAgentDefinitionId(UUID issueId, UUID agentDefinitionId);
}
