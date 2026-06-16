package com.mesha.api.repository;

import com.mesha.api.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query("""
           SELECT c FROM Comment c
           LEFT JOIN FETCH c.author
           WHERE c.issue.id = :issueId
           ORDER BY c.createdAt ASC
           """)
    List<Comment> findAllByIssueId(@Param("issueId") UUID issueId);
}
