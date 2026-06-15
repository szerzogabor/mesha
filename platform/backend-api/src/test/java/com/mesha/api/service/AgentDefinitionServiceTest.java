package com.mesha.api.service;

import com.mesha.api.dto.CreateAgentDefinitionRequest;
import com.mesha.api.dto.UpdateAgentDefinitionRequest;
import com.mesha.api.model.AgentDefinition;
import com.mesha.api.model.AgentProviderType;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.AgentDefinitionRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentDefinitionServiceTest {

    @Mock private AgentDefinitionRepository agentDefinitionRepository;
    @Mock private WorkspaceRepository workspaceRepository;

    private AgentDefinitionService service;
    private UUID workspaceId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AgentDefinitionService(agentDefinitionRepository, workspaceRepository);
        workspaceId = UUID.randomUUID();
        workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", workspaceId);
    }

    @Test
    void create_success() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(agentDefinitionRepository.existsByWorkspaceIdAndName(workspaceId, "senior-dev")).thenReturn(false);
        when(agentDefinitionRepository.save(any())).thenAnswer(inv -> {
            AgentDefinition a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            return a;
        });

        var req = new CreateAgentDefinitionRequest(
            "Senior Developer", "senior-dev", "A senior dev",
            AgentProviderType.BLOCKS, "You are a senior dev.", Map.of("startupCommands", List.of("/sonnet")), null, true
        );
        AgentDefinition result = service.create(workspaceId, req);

        assertThat(result.getName()).isEqualTo("senior-dev");
        assertThat(result.getTitle()).isEqualTo("Senior Developer");
        assertThat(result.getProviderType()).isEqualTo(AgentProviderType.BLOCKS);
        assertThat(result.getProviderParameters()).containsKey("startupCommands");

        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(agentDefinitionRepository).save(captor.capture());
        assertThat(captor.getValue().getWorkspace()).isEqualTo(workspace);
    }

    @Test
    void create_duplicateName_throwsConflict() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(agentDefinitionRepository.existsByWorkspaceIdAndName(workspaceId, "senior-dev")).thenReturn(true);

        var req = new CreateAgentDefinitionRequest(
            "Senior Developer", "senior-dev", null,
            AgentProviderType.BLOCKS, "prompt", null, null, null
        );

        assertThatThrownBy(() -> service.create(workspaceId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void create_workspaceNotFound_throws() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        var req = new CreateAgentDefinitionRequest(
            "Dev", "dev", null,
            AgentProviderType.BLOCKS, "prompt", null, null, null
        );

        assertThatThrownBy(() -> service.create(workspaceId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Workspace not found");
    }

    @Test
    void update_success() {
        UUID agentId = UUID.randomUUID();
        AgentDefinition existing = new AgentDefinition();
        ReflectionTestUtils.setField(existing, "id", agentId);
        existing.setWorkspace(workspace);
        existing.setName("old-name");
        existing.setTitle("Old Title");
        existing.setProviderType(AgentProviderType.BLOCKS);
        existing.setSystemPrompt("old prompt");

        when(agentDefinitionRepository.findById(agentId)).thenReturn(Optional.of(existing));
        when(agentDefinitionRepository.existsByWorkspaceIdAndName(workspaceId, "new-name")).thenReturn(false);
        when(agentDefinitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new UpdateAgentDefinitionRequest(
            "New Title", "new-name", "new desc",
            null, "new prompt", null, null, false
        );
        AgentDefinition result = service.update(workspaceId, agentId, req);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getSystemPrompt()).isEqualTo("new prompt");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void update_wrongWorkspace_throws() {
        UUID agentId = UUID.randomUUID();
        Workspace otherWorkspace = new Workspace();
        ReflectionTestUtils.setField(otherWorkspace, "id", UUID.randomUUID());

        AgentDefinition existing = new AgentDefinition();
        ReflectionTestUtils.setField(existing, "id", agentId);
        existing.setWorkspace(otherWorkspace);

        when(agentDefinitionRepository.findById(agentId)).thenReturn(Optional.of(existing));

        var req = new UpdateAgentDefinitionRequest(null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(workspaceId, agentId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void delete_success() {
        UUID agentId = UUID.randomUUID();
        AgentDefinition existing = new AgentDefinition();
        ReflectionTestUtils.setField(existing, "id", agentId);
        existing.setWorkspace(workspace);

        when(agentDefinitionRepository.findById(agentId)).thenReturn(Optional.of(existing));

        service.delete(workspaceId, agentId);

        verify(agentDefinitionRepository).delete(existing);
    }

    @Test
    void listByWorkspace_returnsList() {
        AgentDefinition a1 = new AgentDefinition();
        a1.setName("agent-1");
        AgentDefinition a2 = new AgentDefinition();
        a2.setName("agent-2");

        when(agentDefinitionRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId))
            .thenReturn(List.of(a1, a2));

        List<AgentDefinition> result = service.listByWorkspace(workspaceId);

        assertThat(result).hasSize(2);
    }
}
