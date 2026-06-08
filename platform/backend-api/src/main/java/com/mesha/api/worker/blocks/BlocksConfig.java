package com.mesha.api.worker.blocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Required environment variables for session polling to be active:
 *   BLOCKS_API_URL — base URL of the Blocks REST API
 *   BLOCKS_API_KEY — bearer token for authentication
 *
 * When these are absent (e.g. local development without Blocks integration),
 * the RestClient is still created but session dispatch will fail gracefully
 * with logged errors rather than crashing the service.
 */
@Configuration
class BlocksConfig {

    private static final Logger log = LoggerFactory.getLogger(BlocksConfig.class);

    @Value("${mesha.blocks.api-url:}")
    private String apiUrl;

    @Value("${mesha.blocks.api-key:}")
    private String apiKey;

    @Bean("blocksRestClient")
    RestClient blocksRestClient() {
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("BLOCKS_API_URL is not configured — Blocks session polling will not dispatch sessions");
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BLOCKS_API_KEY is not configured — Blocks session polling will not dispatch sessions");
        }

        String effectiveUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : "http://localhost:0";

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(effectiveUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
