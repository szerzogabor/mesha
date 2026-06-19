package com.mesha.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Arrays;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConnectorApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ConnectorApplication.class);
        if (Arrays.asList(args).contains("login")) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
