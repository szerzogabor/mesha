package com.mesha.connector.cli;

import com.mesha.connector.agent.AgentRegistration;
import com.mesha.connector.agent.AgentRegistrationStore;
import com.mesha.connector.auto.AutoConnectException;
import com.mesha.connector.auto.AutoConnectService;
import com.mesha.connector.session.SessionPollingLoop;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles {@code poll} invocations, e.g. {@code java -jar mesha-connector.jar poll}. When
 * auto-connect is enabled, performs login and agent registration automatically before starting
 * the polling loop. Otherwise, the connector must have been authenticated and registered via
 * prior {@code login} / {@code register} commands.
 */
@Component
public class PollCommandRunner implements ApplicationRunner {

    private final AgentRegistrationStore agentRegistrationStore;
    private final SessionPollingLoop sessionPollingLoop;
    private final AutoConnectService autoConnectService;

    public PollCommandRunner(AgentRegistrationStore agentRegistrationStore,
                             SessionPollingLoop sessionPollingLoop,
                             AutoConnectService autoConnectService) {
        this.agentRegistrationStore = agentRegistrationStore;
        this.sessionPollingLoop = sessionPollingLoop;
        this.autoConnectService = autoConnectService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("poll")) {
            return;
        }

        try {
            autoConnectService.ensureConnected();
        } catch (AutoConnectException e) {
            System.err.println("Auto-connect failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        Optional<AgentRegistration> registration = agentRegistrationStore.load();
        if (registration.isEmpty()) {
            System.err.println("No agent registration found. Run `register` first, or set CONNECTOR_AUTO_CONNECT_ENABLED=true.");
            System.exit(1);
            return;
        }

        System.out.println("Polling for sessions as agent " + registration.get().agentId() + "...");
        sessionPollingLoop.run(registration.get().agentId());
    }
}
