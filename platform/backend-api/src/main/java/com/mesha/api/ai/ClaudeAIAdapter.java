package com.mesha.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
public class ClaudeAIAdapter implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAIAdapter.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DraftPromptSupport prompts;
    private final String model;
    private final String apiKey;

    public ClaudeAIAdapter(
            @Value("${ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${ai.anthropic.model:claude-haiku-4-5-20251001}") String model,
            @Value("${ai.anthropic.api-key:}") String apiKey,
            ObjectMapper objectMapper,
            DraftPromptSupport prompts) {
        this.model = model;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.prompts = prompts;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public AIDraftContent generateTicketDraft(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider not configured");
        }

        String systemPrompt = prompts.systemPrompt();
        String userMessage = prompts.userMessage(prompt);

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "max_tokens", 2048,
            "system", systemPrompt,
            "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        try {
            String responseBody = restClient.post()
                .uri("/v1/messages")
                .body(requestBody)
                .retrieve()
                .body(String.class);

            return parseResponse(responseBody);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call Claude API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI generation failed: " + e.getMessage());
        }
    }

    private AIDraftContent parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("content").get(0).path("text").asText();
            return prompts.parseContent(text);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Claude API response: {}", responseBody, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse AI response");
        }
    }
}
