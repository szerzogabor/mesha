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
 * instead of creating a duplicate. Hostname is auto-detected via reverse DNS lookup unless
 * overridden with {@code --hostname=<name>}.
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
            System.err.println("Usage: register --executor-type=<type> [--capabilities=a,b,c] [--hostname=<name>]");
            System.exit(1);
            return;
        }

        List<String> capabilityValues = args.getOptionValues("capabilities");
        List<String> capabilities = (capabilityValues == null || capabilityValues.isEmpty())
                ? List.of()
                : capabilityValues.stream()
                        .flatMap(val -> Arrays.stream(val.split(",")))
                        .map(String::trim)
                        .filter(val -> !val.isEmpty())
                        .toList();

        try {
            String hostname = resolveHostname(args);
            AgentResponse agent = agentRegistrationService.register(hostname, executorTypeValues.get(0), capabilities);
            System.out.println("Registered agent " + agent.id() + " (" + agent.hostname() + "/" + agent.executorType() + ")");
        } catch (AgentRegistrationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private String resolveHostname(ApplicationArguments args) {
        List<String> hostnameValues = args.getOptionValues("hostname");
        if (hostnameValues != null && !hostnameValues.isEmpty() && !hostnameValues.get(0).isBlank()) {
            return hostnameValues.get(0);
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String envHostname = System.getenv("HOSTNAME");
            if (envHostname == null || envHostname.isBlank()) {
                envHostname = System.getenv("COMPUTERNAME");
            }
            if (envHostname != null && !envHostname.isBlank()) {
                return envHostname;
            }
            return "unknown-host";
        }
    }
}
