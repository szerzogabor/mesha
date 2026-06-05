package com.mesha.worker.blocks;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configures the HTTP client for the Blocks AI platform.
 *
 * Required environment variables (fail-fast at startup if absent or blank):
 *   BLOCKS_API_URL — base URL of the Blocks REST API
 *   BLOCKS_API_KEY — bearer token for authentication
 */
@Configuration
class BlocksConfig {

    @Value("${mesha.blocks.api-url}")
    private String apiUrl;

    @Value("${mesha.blocks.api-key}")
    private String apiKey;

    @PostConstruct
    void validate() {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("BLOCKS_API_URL must not be blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("BLOCKS_API_KEY must not be blank");
        }
    }

    @Bean("blocksRestClient")
    RestClient blocksRestClient() {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
