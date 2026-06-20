package com.mesha.api.worker.scheduling;

import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.service.GitHubPullRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PrSyncSchedulerTest {

    @Mock
    private GitHubRepositoryRepository repositoryRepo;

    @Mock
    private GitHubPullRequestService prService;

    private PrSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scheduler = new PrSyncScheduler(repositoryRepo, prService);
    }

    @Test
    void syncAllRepositories_syncsEachConnectedRepository() {
        UUID repoId1 = UUID.randomUUID();
        UUID repoId2 = UUID.randomUUID();
        GitHubRepository repo1 = repoWithId(repoId1);
        GitHubRepository repo2 = repoWithId(repoId2);

        when(repositoryRepo.findAllByConnectedTrue()).thenReturn(List.of(repo1, repo2));

        scheduler.syncAllRepositories();

        verify(prService).syncPullRequests(repoId1);
        verify(prService).syncPullRequests(repoId2);
    }

    @Test
    void syncAllRepositories_skipsWhenNoRepositories() {
        when(repositoryRepo.findAllByConnectedTrue()).thenReturn(List.of());

        scheduler.syncAllRepositories();

        verifyNoInteractions(prService);
    }

    @Test
    void syncAllRepositories_continuesAfterSingleRepoFailure() {
        UUID repoId1 = UUID.randomUUID();
        UUID repoId2 = UUID.randomUUID();
        when(repositoryRepo.findAllByConnectedTrue()).thenReturn(List.of(repoWithId(repoId1), repoWithId(repoId2)));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
                .when(prService).syncPullRequests(repoId1);

        scheduler.syncAllRepositories();

        verify(prService).syncPullRequests(repoId1);
        verify(prService).syncPullRequests(repoId2);
    }

    @Test
    void syncAllRepositories_handlesQueryFailureGracefully() {
        when(repositoryRepo.findAllByConnectedTrue()).thenThrow(new RuntimeException("DB error"));

        scheduler.syncAllRepositories();

        verifyNoInteractions(prService);
    }

    private GitHubRepository repoWithId(UUID id) {
        GitHubRepository repo = mock(GitHubRepository.class);
        when(repo.getId()).thenReturn(id);
        return repo;
    }
}
