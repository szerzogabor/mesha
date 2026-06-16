package com.mesha.api.service;

import com.mesha.api.dto.GitHubRepositoryDto;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class GitHubRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryService.class);

    private final GitHubRepositoryRepository repositoryRepo;

    public GitHubRepositoryService(GitHubRepositoryRepository repositoryRepo) {
        this.repositoryRepo = repositoryRepo;
    }

    public List<GitHubRepositoryDto> listForWorkspace(UUID workspaceId) {
        long startMs = System.currentTimeMillis();
        List<GitHubRepositoryDto> repos = repositoryRepo.findAllByWorkspaceIdAndConnectedTrue(workspaceId)
                .stream().map(GitHubRepositoryDto::from).toList();
        long durationMs = System.currentTimeMillis() - startMs;
        log.info("Listed repositories connected={} durationMs={}", repos.size(), durationMs);
        return repos;
    }

    public GitHubRepositoryDto getById(UUID repositoryId) {
        log.debug("Fetching repository repositoryId={}", repositoryId);
        return repositoryRepo.findById(repositoryId)
                .map(GitHubRepositoryDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
    }

    @Transactional
    public void disconnect(UUID repositoryId) {
        log.info("Disconnecting repository repositoryId={}", repositoryId);
        GitHubRepository repo = repositoryRepo.findById(repositoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
        repo.setConnected(false);
        repositoryRepo.save(repo);
        log.info("Repository disconnected repositoryId={} fullName={}", repositoryId, repo.getFullName());
    }
}
