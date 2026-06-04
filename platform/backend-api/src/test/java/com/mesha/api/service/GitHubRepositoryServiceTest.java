package com.mesha.api.service;

import com.mesha.api.github.GitHubInstallationStatus;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.GitHubRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubRepositoryServiceTest {

    @Mock
    private GitHubRepositoryRepository repositoryRepo;

    private GitHubRepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        repositoryService = new GitHubRepositoryService(repositoryRepo);
    }

    @Test
    void listForWorkspace_returnsOnlyConnectedReposWithActiveInstallation() {
        UUID workspaceId = UUID.randomUUID();

        GitHubInstallation activeInstallation = installationWithStatus(GitHubInstallationStatus.ACTIVE);
        GitHubInstallation uninstalledInstallation = installationWithStatus(GitHubInstallationStatus.UNINSTALLED);

        GitHubRepository visible = connectedRepo(activeInstallation);
        GitHubRepository disconnected = connectedRepo(activeInstallation);
        disconnected.setConnected(false);
        GitHubRepository staleFromUninstalled = connectedRepo(uninstalledInstallation);

        when(repositoryRepo.findConnectedWithActiveInstallationByWorkspaceId(workspaceId))
                .thenReturn(List.of(visible));

        assertThat(repositoryService.listForWorkspace(workspaceId))
                .hasSize(1)
                .extracting(dto -> dto.fullName())
                .containsExactly(visible.getFullName());
    }

    private static GitHubInstallation installationWithStatus(String status) {
        GitHubInstallation installation = new GitHubInstallation();
        installation.setStatus(status);
        return installation;
    }

    private static GitHubRepository connectedRepo(GitHubInstallation installation) {
        Workspace workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID());

        GitHubRepository repo = new GitHubRepository();
        repo.setWorkspace(workspace);
        repo.setInstallation(installation);
        repo.setConnected(true);
        repo.setFullName("org/" + installation.getStatus() + "-repo");
        repo.setOwner("org");
        repo.setName(installation.getStatus() + "-repo");
        repo.setGithubRepoId(1L);
        repo.setHtmlUrl("https://github.com/org/repo");
        return repo;
    }
}
