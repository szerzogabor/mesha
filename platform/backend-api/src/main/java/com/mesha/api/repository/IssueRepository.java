package com.mesha.api.repository;

import com.mesha.api.model.Issue;
import com.mesha.api.model.IssuePriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    @Query(
        value = """
               SELECT i FROM Issue i
               LEFT JOIN FETCH i.assignee
               WHERE i.project.id = :projectId
                 AND (:status IS NULL OR i.status = :status)
                 AND (:priority IS NULL OR i.priority = :priority)
                 AND (:assigneeId IS NULL OR i.assignee.id = :assigneeId)
                 AND (:search IS NULL OR LOWER(i.title) LIKE :search)
                 AND (:#{#labelIds == null || #labelIds.isEmpty()} = true OR EXISTS (
                   SELECT l FROM i.labels l WHERE l.id IN :labelIds
                 ))
               ORDER BY i.createdAt DESC
               """,
        countQuery = """
               SELECT COUNT(i) FROM Issue i
               WHERE i.project.id = :projectId
                 AND (:status IS NULL OR i.status = :status)
                 AND (:priority IS NULL OR i.priority = :priority)
                 AND (:assigneeId IS NULL OR i.assignee.id = :assigneeId)
                 AND (:search IS NULL OR LOWER(i.title) LIKE :search)
                 AND (:#{#labelIds == null || #labelIds.isEmpty()} = true OR EXISTS (
                   SELECT l FROM i.labels l WHERE l.id IN :labelIds
                 ))
               """
    )
    Page<Issue> findByProjectFiltered(
        @Param("projectId") UUID projectId,
        @Param("status") String status,
        @Param("priority") IssuePriority priority,
        @Param("assigneeId") UUID assigneeId,
        @Param("search") String search,
        @Param("labelIds") List<UUID> labelIds,
        Pageable pageable
    );

    long countByProjectId(UUID projectId);

    @Query("SELECT COALESCE(MAX(i.number), 0) + 1 FROM Issue i WHERE i.project.id = :projectId")
    Integer nextNumberForProject(@Param("projectId") UUID projectId);

    @Query("""
           SELECT i FROM Issue i
           WHERE i.project.workspace.id = :workspaceId
             AND UPPER(i.project.key) = :projectKey
             AND i.number = :issueNumber
           """)
    Optional<Issue> findByWorkspaceAndProjectKeyAndNumber(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectKey") String projectKey,
            @Param("issueNumber") Integer issueNumber);
}
