package com.mesha.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared prompt + response parsing for AI ticket-draft generation, so every
 * provider (Claude, OpenAI, ...) produces the identical {@link AIDraftContent}
 * shape from the same instructions.
 */
@Component
public class DraftPromptSupport {

    private final ObjectMapper objectMapper;

    public DraftPromptSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String systemPrompt() {
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

    public String userMessage(String userPrompt) {
        return "Generate an issue ticket for the following request:\n\n" + userPrompt;
    }

    /**
     * Parse a model's text output into {@link AIDraftContent}. Robust to markdown
     * fences and surrounding prose — extracts the outermost JSON object.
     */
    public AIDraftContent parseContent(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI returned empty response");
        }
        try {
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse AI response");
        }
    }
}
