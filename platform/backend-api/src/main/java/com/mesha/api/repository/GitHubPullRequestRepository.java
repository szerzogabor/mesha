package com.mesha.api.repository;

import com.mesha.api.model.GitHubPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequest, UUID> {
    List<GitHubPullRequest> findAllByRepositoryId(UUID repositoryId);
    List<GitHubPullRequest> findAllByRepositoryIdAndState(UUID repositoryId, String state);
    Optional<GitHubPullRequest> findByRepositoryIdAndGithubPrNumber(UUID repositoryId, Integer githubPrNumber);
}
