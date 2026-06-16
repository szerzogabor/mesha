package com.mesha.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.GitHubAppProperties;
import com.mesha.api.dto.GitHubInstallationDto;
import com.mesha.api.model.GitHubInstallation;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
                installation(77L, "suspended", workspace)
        ));

        List<GitHubInstallationDto> result = service.listInstallations(workspaceId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(GitHubInstallationDto::status)
                .containsExactlyInAnyOrder("active", "suspended");
    }

    @Test
    void listInstallationsReturnsEmptyListWhenNoneExist() {
        UUID workspaceId = UUID.randomUUID();
        when(installationRepo.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        assertThat(service.listInstallations(workspaceId)).isEmpty();
    }

    // ---- deleteInstallation (public) --------------------------------------

    @Test
    void deleteInstallationPhysicallyRemovesTheRecord() {
        Long installationId = 137966267L;
        GitHubInstallation installation = activeInstallation(installationId);
        when(installationRepo.findByInstallationId(installationId)).thenReturn(Optional.of(installation));

        service.deleteInstallation(installationId);

        verify(installationRepo).delete(installation);
        // DB CASCADE handles repos and PRs — no manual repo iteration needed
        verifyNoInteractions(repositoryRepo, auditLogService);
    }

    @Test
    void deleteInstallationDoesNothingWhenNotFound() {
        Long installationId = 999L;
        when(installationRepo.findByInstallationId(installationId)).thenReturn(Optional.empty());

        service.deleteInstallation(installationId);

        verify(installationRepo, never()).delete(any());
        verifyNoInteractions(repositoryRepo, auditLogService);
    }

    // ---- autoDeleteInstallation (private) ---------------------------------

    @Test
    void autoDeleteInstallationPhysicallyRemovesTheRecord() {
        Long installationId = 137966267L;
        GitHubInstallation installation = activeInstallation(installationId);
        when(installationRepo.findByInstallationId(installationId)).thenReturn(Optional.of(installation));

        ReflectionTestUtils.invokeMethod(service, "autoDeleteInstallation", installationId);

        verify(installationRepo).delete(installation);
        verifyNoInteractions(repositoryRepo, auditLogService);
    }

    @Test
    void autoDeleteInstallationDoesNothingWhenNotInDb() {
        Long installationId = 999L;
        when(installationRepo.findByInstallationId(installationId)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "autoDeleteInstallation", installationId);

        verify(installationRepo, never()).delete(any());
        verifyNoInteractions(repositoryRepo, auditLogService);
    }

    // ---- helpers ----------------------------------------------------------

    private GitHubInstallation activeInstallation(Long installationId) {
        return installation(installationId, "active", null);
    }

    private GitHubInstallation installation(Long installationId, String status, Workspace workspace) {
        GitHubInstallation inst = new GitHubInstallation();
        ReflectionTestUtils.setField(inst, "id", UUID.randomUUID());
        inst.setInstallationId(installationId);
        inst.setAppId(12345L);
        inst.setAccountLogin("account-" + installationId);
        inst.setStatus(status);
        if (workspace != null) {
            inst.setWorkspace(workspace);
        }
        return inst;
    }
}
