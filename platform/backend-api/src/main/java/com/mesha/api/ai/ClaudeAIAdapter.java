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
    private final String model;
    private final String apiKey;

    public ClaudeAIAdapter(
            @Value("${ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${ai.anthropic.model:claude-haiku-4-5-20251001}") String model,
            @Value("${ai.anthropic.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
        this.model = model;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
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

        String systemPrompt = buildSystemPrompt();
        String userMessage = buildUserMessage(prompt);

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

            // Extract the JSON object robustly — handles markdown fences and surrounding prose
            String json = text.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                json = json.substring(start, end + 1);
            }

            JsonNode parsed = objectMapper.readTree(json);

            return new AIDraftContent(
                parsed.path("title").asText(""),
                parsed.path("description").asText(""),
                parsed.path("acceptanceCriteria").asText(""),
                parsed.path("suggestedLabels").toString(),
                parsed.path("prioritySuggestion").asText("MEDIUM"),
                parsed.path("implementationNotes").asText(""),
                parsed.path("scopeNotes").asText(""),
                parsed.path("outOfScopeNotes").asText("")
            );
        } catch (Exception e) {
            log.error("Failed to parse Claude API response: {}", responseBody, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse AI response");
        }
    }

    private String buildSystemPrompt() {
        return """
            You are a senior technical product manager. Generate detailed software issue tickets from user requests.
            Always respond with a single valid JSON object. Do not include any text outside the JSON.
            The JSON must have exactly these fields:
            - title: concise issue title (max 150 characters)
            - description: 2-4 sentence technical summary of the issue/feature
            - acceptanceCriteria: acceptance criteria in markdown bullet format (each criterion on its own line starting with "- ")
            - suggestedLabels: JSON array of 1-4 relevant label strings (e.g. ["backend", "api", "auth"])
            - prioritySuggestion: one of URGENT, HIGH, MEDIUM, LOW
            - implementationNotes: technical guidance for implementation (2-4 sentences)
            - scopeNotes: what is explicitly in scope (markdown bullet list)
            - outOfScopeNotes: what is explicitly out of scope (markdown bullet list)
            """;
    }

    private String buildUserMessage(String userPrompt) {
        return "Generate an issue ticket for the following request:\n\n" + userPrompt;
    }
}
