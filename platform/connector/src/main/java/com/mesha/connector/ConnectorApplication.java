package com.mesha.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConnectorApplication {

    private static final List<String> CLI_COMMANDS = List.of("login", "register", "heartbeat", "poll");

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ConnectorApplication.class);
        if (Arrays.stream(args).anyMatch(CLI_COMMANDS::contains)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
