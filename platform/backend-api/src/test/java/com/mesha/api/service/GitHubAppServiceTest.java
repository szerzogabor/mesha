package com.mesha.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.GitHubAppProperties;
import com.mesha.api.dto.GitHubInstallationDto;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubAppServiceTest {

    @Mock private GitHubAppProperties props;
    @Mock private GitHubInstallationRepository installationRepo;
    @Mock private GitHubRepositoryRepository repositoryRepo;
    @Mock private WorkspaceRepository workspaceRepo;
    @Mock private GitHubAuditLogService auditLogService;

    private GitHubAppService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new GitHubAppService(props, installationRepo, repositoryRepo, workspaceRepo,
                auditLogService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ---- listInstallations ------------------------------------------------

    @Test
    void listInstallationsReturnsAllStatuses() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", workspaceId);

        when(installationRepo.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(
                installation(42L, "active", workspace),
                installation(99L, "deleted", workspace),
                installation(77L, "suspended", workspace)
        ));

        List<GitHubInstallationDto> result = service.listInstallations(workspaceId);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(GitHubInstallationDto::status)
                .containsExactlyInAnyOrder("active", "deleted", "suspended");
    }

    @Test
    void listInstallationsReturnsEmptyListWhenNoneExist() {
        UUID workspaceId = UUID.randomUUID();
        when(installationRepo.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        assertThat(service.listInstallations(workspaceId)).isEmpty();
    }

    // ---- autoMarkInstallationDeleted (private) ----------------------------

    @Test
    void autoMarkInstallationDeletedUpdatesStatusAndDisconnectsConnectedRepos() {
        Long installationId = 137966267L;
        UUID installationDbId = UUID.randomUUID();

        GitHubInstallation installation = new GitHubInstallation();
        ReflectionTestUtils.setField(installation, "id", installationDbId);
        installation.setInstallationId(installationId);
        installation.setAccountLogin("szerzogabor");
        installation.setStatus("active");

        GitHubRepository connectedRepo = new GitHubRepository();
        connectedRepo.setConnected(true);
        connectedRepo.setFullName("szerzogabor/mesha");

        GitHubRepository alreadyDisconnected = new GitHubRepository();
        alreadyDisconnected.setConnected(false);
        alreadyDisconnected.setFullName("szerzogabor/other");

        when(installationRepo.findByInstallationId(installationId)).thenReturn(Optional.of(installation));
        when(repositoryRepo.findAllByInstallationId(installationDbId))
                .thenReturn(List.of(connectedRepo, alreadyDisconnected));

        ReflectionTestUtils.invokeMethod(service, "autoMarkInstallationDeleted", installationId);

        assertThat(installation.getStatus()).isEqualTo("deleted");
        verify(installationRepo).save(installation);

        // Only the connected repo should be saved (disconnected one is skipped)
        ArgumentCaptor<GitHubRepository> repoCaptor = ArgumentCaptor.forClass(GitHubRepository.class);
        verify(repositoryRepo, times(1)).save(repoCaptor.capture());
        assertThat(repoCaptor.getValue().getConnected()).isFalse();
        assertThat(repoCaptor.getValue().getFullName()).isEqualTo("szerzogabor/mesha");

        verify(auditLogService).log(eq(installation),
                eq(GitHubAuditLogService.REPOSITORY_DETACHED), eq("szerzogabor/mesha"));
        verify(auditLogService).log(eq(installation),
                eq(GitHubAuditLogService.INSTALLATION_DELETED), eq("auto-detected stale installation"));
        // Already-disconnected repo must not generate an audit event
        verify(auditLogService, never()).log(any(), eq(GitHubAuditLogService.REPOSITORY_DETACHED),
                eq("szerzogabor/other"));
    }

    @Test
    void autoMarkInstallationDeletedIsIdempotentWhenAlreadyDeleted() {
        Long installationId = 111L;

        GitHubInstallation installation = new GitHubInstallation();
        ReflectionTestUtils.setField(installation, "id", UUID.randomUUID());
        installation.setInstallationId(installationId);
        installation.setStatus("deleted");

        when(installationRepo.findByInstallationId(installationId)).thenReturn(Optional.of(installation));

        ReflectionTestUtils.invokeMethod(service, "autoMarkInstallationDeleted", installationId);

        verify(installationRepo, never()).save(any());
        verifyNoInteractions(repositoryRepo, auditLogService);
    }

    @Test
    void autoMarkInstallationDeletedDoesNothingWhenInstallationNotInDb() {
        Long installationId = 999L;
        when(installationRepo.findByInstallationId(installationId)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "autoMarkInstallationDeleted", installationId);

        verify(installationRepo, never()).save(any());
        verifyNoInteractions(repositoryRepo, auditLogService);
    }

    // ---- helpers ----------------------------------------------------------

    private GitHubInstallation installation(Long installationId, String status, Workspace workspace) {
        GitHubInstallation inst = new GitHubInstallation();
        ReflectionTestUtils.setField(inst, "id", UUID.randomUUID());
        inst.setInstallationId(installationId);
        inst.setAppId(12345L);
        inst.setAccountLogin("account-" + installationId);
        inst.setStatus(status);
        inst.setWorkspace(workspace);
        return inst;
    }
}
