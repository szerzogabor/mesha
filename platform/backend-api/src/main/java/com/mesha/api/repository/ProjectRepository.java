package com.mesha.api.repository;

import com.mesha.api.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);
    boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
}
