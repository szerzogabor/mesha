package com.mesha.connector.cli;

import com.mesha.connector.agent.AgentRegistration;
import com.mesha.connector.agent.AgentRegistrationStore;
import com.mesha.connector.session.SessionPollingLoop;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles {@code poll} invocations, e.g. {@code java -jar mesha-connector.jar poll}. Runs the
 * session polling loop for the agent identity persisted by a prior {@code register} call until
 * the process is terminated.
 */
@Component
public class PollCommandRunner implements ApplicationRunner {

    private final AgentRegistrationStore agentRegistrationStore;
    private final SessionPollingLoop sessionPollingLoop;

    public PollCommandRunner(AgentRegistrationStore agentRegistrationStore, SessionPollingLoop sessionPollingLoop) {
        this.agentRegistrationStore = agentRegistrationStore;
        this.sessionPollingLoop = sessionPollingLoop;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("poll")) {
            return;
        }

        Optional<AgentRegistration> registration = agentRegistrationStore.load();
        if (registration.isEmpty()) {
            System.err.println("No agent registration found. Run `register` first.");
            System.exit(1);
            return;
        }

        System.out.println("Polling for sessions as agent " + registration.get().agentId() + "...");
        sessionPollingLoop.run(registration.get().agentId());
    }
}
