package com.mesha.api.service;

import com.mesha.api.dto.GitHubRepositoryDto;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class GitHubRepositoryService {

    private final GitHubRepositoryRepository repositoryRepo;

    public GitHubRepositoryService(GitHubRepositoryRepository repositoryRepo) {
        this.repositoryRepo = repositoryRepo;
    }

    public List<GitHubRepositoryDto> listForWorkspace(UUID workspaceId) {
        return repositoryRepo.findAllByWorkspaceId(workspaceId)
                .stream().map(GitHubRepositoryDto::from).toList();
    }

    public GitHubRepositoryDto getById(UUID repositoryId) {
        return repositoryRepo.findById(repositoryId)
                .map(GitHubRepositoryDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
    }

    @Transactional
    public void disconnect(UUID repositoryId) {
        GitHubRepository repo = repositoryRepo.findById(repositoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
        repo.setConnected(false);
        repositoryRepo.save(repo);
    }
}
