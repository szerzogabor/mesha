package com.mesha.api.repository;

import com.mesha.api.model.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);
    boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
    boolean existsByWorkspaceIdAndKey(UUID workspaceId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") UUID id);
}
