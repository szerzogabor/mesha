package com.mesha.connector.cli;

import com.mesha.connector.agent.AgentRegistrationException;
import com.mesha.connector.agent.AgentRegistrationService;
import com.mesha.connector.agent.AgentResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Handles {@code register} invocations, e.g.
 * {@code java -jar mesha-connector.jar register --executor-type=cli --capabilities=qwen,shell}.
 * Registers this machine/executor combination as a Mesha agent, persisting the agent's
 * identity locally so subsequent {@code register} or {@code heartbeat} calls reconnect to it
 * instead of creating a duplicate.
 */
@Component
public class RegisterCommandRunner implements ApplicationRunner {

    private final AgentRegistrationService agentRegistrationService;

    public RegisterCommandRunner(AgentRegistrationService agentRegistrationService) {
        this.agentRegistrationService = agentRegistrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("register")) {
            return;
        }

        List<String> executorTypeValues = args.getOptionValues("executor-type");
        if (executorTypeValues == null || executorTypeValues.isEmpty()) {
            System.err.println("Usage: register --executor-type=<type> [--capabilities=a,b,c]");
            System.exit(1);
            return;
        }

        List<String> capabilityValues = args.getOptionValues("capabilities");
        List<String> capabilities = (capabilityValues == null || capabilityValues.isEmpty())
                ? List.of()
                : Arrays.asList(capabilityValues.get(0).split(","));

        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            AgentResponse agent = agentRegistrationService.register(hostname, executorTypeValues.get(0), capabilities);
            System.out.println("Registered agent " + agent.id() + " (" + agent.hostname() + "/" + agent.executorType() + ")");
        } catch (UnknownHostException e) {
            System.err.println("Registration failed: could not determine local hostname (" + e.getMessage() + ")");
            System.exit(1);
        } catch (AgentRegistrationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
