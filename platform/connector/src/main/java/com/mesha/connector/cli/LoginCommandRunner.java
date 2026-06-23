package com.mesha.connector.cli;

import com.mesha.connector.auth.ConnectorAuthException;
import com.mesha.connector.auth.ConnectorAuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles {@code login} invocations, e.g. {@code java -jar mesha-connector.jar login --token=<token>}.
 * The supplied token is the connector access token ({@code mcat_...}) copied from the
 * "Connector Access Token" generator in the Mesha web app; it is validated against the
 * backend and stored locally as-is.
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
            System.err.println("Usage: login --token=<connector-access-token>");
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
