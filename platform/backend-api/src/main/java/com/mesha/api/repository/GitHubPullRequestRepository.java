package com.mesha.api.repository;

import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequest, UUID> {
    List<GitHubPullRequest> findAllByRepositoryId(UUID repositoryId);
    List<GitHubPullRequest> findAllByRepositoryIdAndState(UUID repositoryId, String state);
    Optional<GitHubPullRequest> findByRepositoryIdAndGithubPrNumber(UUID repositoryId, Integer githubPrNumber);
    Optional<GitHubPullRequest> findByHtmlUrl(String htmlUrl);
    Optional<GitHubPullRequest> findByBlocksSession(BlocksSession session);
    Optional<GitHubPullRequest> findByBlocksSessionId(UUID blocksSessionId);
    List<GitHubPullRequest> findAllByBlocksSessionId(UUID blocksSessionId);
    List<GitHubPullRequest> findBySourceBranchAndBlocksSessionIsNull(String sourceBranch);
    List<GitHubPullRequest> findBySourceBranch(String sourceBranch);

    @Query("""
            SELECT pr FROM GitHubPullRequest pr
            WHERE pr.repository.id = :repositoryId
              AND (
                :status IS NULL
                OR (:status = 'open'   AND pr.state = 'open')
                OR (:status = 'merged' AND pr.state = 'closed' AND pr.mergedAt IS NOT NULL)
                OR (:status = 'closed' AND pr.state = 'closed' AND pr.mergedAt IS NULL)
              )
            ORDER BY pr.updatedAt DESC
            """)
    List<GitHubPullRequest> findByRepositoryIdAndStatus(@Param("repositoryId") UUID repositoryId,
                                                        @Param("status") String status);

    @Query("""
            SELECT pr FROM GitHubPullRequest pr
            WHERE pr.blocksSession.issue.id IN :issueIds
              AND pr.updatedAt = (
                SELECT MAX(pr2.updatedAt)
                FROM GitHubPullRequest pr2
                WHERE pr2.blocksSession.issue.id = pr.blocksSession.issue.id
              )
            """)
    List<GitHubPullRequest> findLatestByIssueIds(@Param("issueIds") List<UUID> issueIds);

    Optional<GitHubPullRequest> findFirstByBlocksSession_Issue_IdOrderByUpdatedAtDesc(UUID issueId);
}
