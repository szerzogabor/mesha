package com.mesha.api.repository;

import com.mesha.api.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);

    @Query("""
           SELECT w FROM Workspace w
           JOIN WorkspaceMember m ON m.workspace = w
           WHERE m.user.id = :userId
           ORDER BY w.createdAt ASC
           """)
    List<Workspace> findAllByMemberUserId(@Param("userId") UUID userId);
}
