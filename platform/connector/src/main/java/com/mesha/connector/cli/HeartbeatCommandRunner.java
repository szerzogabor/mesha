package com.mesha.connector.cli;

import com.mesha.connector.agent.AgentRegistrationException;
import com.mesha.connector.agent.AgentRegistrationService;
import com.mesha.connector.agent.AgentResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Handles {@code heartbeat} invocations, e.g. {@code java -jar mesha-connector.jar heartbeat}.
 * Sends a single heartbeat for the agent identity persisted by a prior {@code register} call.
 */
@Component
public class HeartbeatCommandRunner implements ApplicationRunner {

    private final AgentRegistrationService agentRegistrationService;

    public HeartbeatCommandRunner(AgentRegistrationService agentRegistrationService) {
        this.agentRegistrationService = agentRegistrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("heartbeat")) {
            return;
        }

        try {
            AgentResponse agent = agentRegistrationService.heartbeat();
            System.out.println("Heartbeat sent for agent " + agent.id() + " (status=" + agent.status() + ")");
        } catch (AgentRegistrationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
