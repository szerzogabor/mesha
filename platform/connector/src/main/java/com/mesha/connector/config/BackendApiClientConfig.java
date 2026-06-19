package com.mesha.connector.config;

import com.mesha.connector.auth.ConnectorAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BackendApiClientConfig {

    @Bean
    public RestClient backendApiRestClient(ConnectorProperties properties, ConnectorAuthInterceptor connectorAuthInterceptor) {
        return RestClient.builder()
                .baseUrl(properties.backendUrl())
                .requestInterceptor(connectorAuthInterceptor)
                .build();
    }
}
