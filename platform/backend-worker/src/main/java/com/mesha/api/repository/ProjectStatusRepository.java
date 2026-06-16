package com.mesha.api.repository;

import com.mesha.api.model.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectStatusRepository extends JpaRepository<ProjectStatus, UUID> {

    List<ProjectStatus> findAllByProjectIdOrderByPositionAsc(UUID projectId);

    Optional<ProjectStatus> findByProjectIdAndName(UUID projectId, String name);

    boolean existsByProjectIdAndName(UUID projectId, String name);

    @Query("SELECT COALESCE(MAX(ps.position), -1) + 1 FROM ProjectStatus ps WHERE ps.project.id = :projectId")
    Integer nextPositionForProject(@Param("projectId") UUID projectId);
}
