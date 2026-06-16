package com.mesha.api.repository;

import com.mesha.api.model.AgentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, UUID> {
    List<AgentDefinition> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    List<AgentDefinition> findAllByWorkspaceIdAndActiveTrueOrderByTitleAsc(UUID workspaceId);
    boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
    Optional<AgentDefinition> findByWorkspaceIdAndName(UUID workspaceId, String name);
}
