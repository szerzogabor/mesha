package com.mesha.connector.auto;

import com.mesha.connector.agent.AgentRegistration;
import com.mesha.connector.agent.AgentRegistrationService;
import com.mesha.connector.agent.AgentRegistrationStore;
import com.mesha.connector.agent.AgentResponse;
import com.mesha.connector.auth.ConnectorAuthService;
import com.mesha.connector.config.AutoConnectProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutoConnectServiceTest {

    @Mock private ConnectorAuthService connectorAuthService;
    @Mock private AgentRegistrationService agentRegistrationService;
    @Mock private AgentRegistrationStore agentRegistrationStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void ensureConnected_disabled_doesNothing() {
        AutoConnectProperties props = new AutoConnectProperties(false, null, null, null, null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        service.ensureConnected();

        verifyNoInteractions(connectorAuthService, agentRegistrationService, agentRegistrationStore);
    }

    @Test
    void ensureConnected_enabled_missingToken_throws() {
        AutoConnectProperties props = new AutoConnectProperties(true, "", "cli", List.of(), null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        assertThatThrownBy(service::ensureConnected)
                .isInstanceOf(AutoConnectException.class)
                .hasMessageContaining("CONNECTOR_AUTO_CONNECT_TOKEN");
    }

    @Test
    void ensureConnected_enabled_missingExecutorType_throws() {
        AutoConnectProperties props = new AutoConnectProperties(true, "jwt-token", "", List.of(), null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        assertThatThrownBy(service::ensureConnected)
                .isInstanceOf(AutoConnectException.class)
                .hasMessageContaining("CONNECTOR_AUTO_CONNECT_EXECUTOR_TYPE");
    }

    @Test
    void ensureConnected_notAuthenticated_performsLogin() {
        AutoConnectProperties props = new AutoConnectProperties(true, "jwt-token", "cli", List.of(), null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        when(connectorAuthService.isAuthenticated()).thenReturn(false);
        when(agentRegistrationStore.load()).thenReturn(Optional.of(existingRegistration()));

        service.ensureConnected();

        verify(connectorAuthService).login("jwt-token");
    }

    @Test
    void ensureConnected_alreadyAuthenticated_skipsLogin() {
        AutoConnectProperties props = new AutoConnectProperties(true, "jwt-token", "cli", List.of(), null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        when(connectorAuthService.isAuthenticated()).thenReturn(true);
        when(agentRegistrationStore.load()).thenReturn(Optional.of(existingRegistration()));

        service.ensureConnected();

        verify(connectorAuthService, never()).login(anyString());
    }

    @Test
    void ensureConnected_notRegistered_performsRegistration() {
        AutoConnectProperties props = new AutoConnectProperties(true, "jwt-token", "docker", List.of("git", "docker"), null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        when(connectorAuthService.isAuthenticated()).thenReturn(true);
        when(agentRegistrationStore.load()).thenReturn(Optional.empty());
        when(agentRegistrationService.register(anyString(), eq("docker"), eq(List.of("git", "docker"))))
                .thenReturn(new AgentResponse(UUID.randomUUID(), "my-host", "docker", "0.0.1", List.of("git", "docker"), "ONLINE", Instant.now(), null));

        service.ensureConnected();

        verify(agentRegistrationService).register(anyString(), eq("docker"), eq(List.of("git", "docker")));
    }

    @Test
    void ensureConnected_alreadyRegistered_skipsRegistration() {
        AutoConnectProperties props = new AutoConnectProperties(true, "jwt-token", "cli", List.of(), null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        when(connectorAuthService.isAuthenticated()).thenReturn(true);
        when(agentRegistrationStore.load()).thenReturn(Optional.of(existingRegistration()));

        service.ensureConnected();

        verify(agentRegistrationService, never()).register(anyString(), anyString(), anyList());
    }

    @Test
    void ensureConnected_hostnameOverride_usedForRegistration() {
        AutoConnectProperties props = new AutoConnectProperties(true, "jwt-token", "cli", List.of(), "my-custom-host");
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        when(connectorAuthService.isAuthenticated()).thenReturn(true);
        when(agentRegistrationStore.load()).thenReturn(Optional.empty());
        when(agentRegistrationService.register(eq("my-custom-host"), anyString(), anyList()))
                .thenReturn(new AgentResponse(UUID.randomUUID(), "my-custom-host", "cli", "0.0.1", List.of(), "ONLINE", Instant.now(), null));

        service.ensureConnected();

        verify(agentRegistrationService).register(eq("my-custom-host"), anyString(), anyList());
    }

    @Test
    void ensureConnected_fullFlow_logsInAndRegisters() {
        AutoConnectProperties props = new AutoConnectProperties(true, "jwt-token", "cli", List.of("git"), null);
        AutoConnectService service = new AutoConnectService(props, connectorAuthService,
                agentRegistrationService, agentRegistrationStore);

        when(connectorAuthService.isAuthenticated()).thenReturn(false);
        when(agentRegistrationStore.load()).thenReturn(Optional.empty());
        when(agentRegistrationService.register(anyString(), eq("cli"), eq(List.of("git"))))
                .thenReturn(new AgentResponse(UUID.randomUUID(), "host", "cli", "0.0.1", List.of("git"), "ONLINE", Instant.now(), null));

        service.ensureConnected();

        verify(connectorAuthService).login("jwt-token");
        verify(agentRegistrationService).register(anyString(), eq("cli"), eq(List.of("git")));
    }

    private AgentRegistration existingRegistration() {
        return new AgentRegistration(UUID.randomUUID(), "my-host", "cli");
    }
}
