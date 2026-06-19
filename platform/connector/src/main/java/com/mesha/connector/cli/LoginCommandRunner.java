package com.mesha.connector.cli;

import com.mesha.connector.auth.ConnectorAuthException;
import com.mesha.connector.auth.ConnectorAuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles {@code login} invocations, e.g. {@code java -jar mesha-connector.jar login --token=<token>}.
 * The supplied token is the bearer token the user already obtained from Mesha (e.g. via the web app);
 * it is exchanged for connector-specific credentials which are then stored locally.
 */
@Component
public class LoginCommandRunner implements ApplicationRunner {

    private final ConnectorAuthService connectorAuthService;

    public LoginCommandRunner(ConnectorAuthService connectorAuthService) {
        this.connectorAuthService = connectorAuthService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains("login")) {
            return;
        }

        List<String> tokenValues = args.getOptionValues("token");
        if (tokenValues == null || tokenValues.isEmpty()) {
            System.err.println("Usage: login --token=<mesha-access-token>");
            System.exit(1);
            return;
        }

        try {
            connectorAuthService.login(tokenValues.get(0));
            System.out.println("Login successful. Connector credentials stored.");
        } catch (ConnectorAuthException e) {
            System.err.println("Login failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
