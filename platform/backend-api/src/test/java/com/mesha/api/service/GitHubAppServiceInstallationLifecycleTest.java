package com.mesha.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.GitHubAppProperties;
import com.mesha.api.github.GitHubInstallationStatus;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAppServiceInstallationLifecycleTest {

    private static final Long GITHUB_INSTALLATION_ID = 42L;

    @Mock
    private GitHubAppProperties props;
    @Mock
    private GitHubInstallationRepository installationRepo;
    @Mock
    private GitHubRepositoryRepository repositoryRepo;
    @Mock
    private WorkspaceRepository workspaceRepo;
    @Mock
    private GitHubAuditLogService auditLogService;

    private GitHubAppService appService;

    @BeforeEach
    void setUp() {
        appService = new GitHubAppService(
                props, installationRepo, repositoryRepo, workspaceRepo, auditLogService, new ObjectMapper());
    }

    @Test
    void markInstallationDeleted_uninstallsAndDisconnectsAllRepositories() {
        UUID installationUuid = UUID.randomUUID();
        GitHubInstallation installation = new GitHubInstallation();
        ReflectionTestUtils.setField(installation, "id", installationUuid);
        installation.setInstallationId(GITHUB_INSTALLATION_ID);
        installation.setStatus(GitHubInstallationStatus.ACTIVE);

        GitHubRepository repo = new GitHubRepository();
        repo.setConnected(true);
        repo.setInstallation(installation);

        when(installationRepo.findByInstallationId(GITHUB_INSTALLATION_ID))
                .thenReturn(Optional.of(installation));
        when(repositoryRepo.findAllByInstallationId(installationUuid)).thenReturn(List.of(repo));
        when(installationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repositoryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appService.markInstallationDeleted(GITHUB_INSTALLATION_ID);

        assertThat(installation.getStatus()).isEqualTo(GitHubInstallationStatus.UNINSTALLED);
        assertThat(repo.getConnected()).isFalse();
        verify(repositoryRepo).save(repo);
    }
}
