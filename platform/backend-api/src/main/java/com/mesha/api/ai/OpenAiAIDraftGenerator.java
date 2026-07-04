package com.mesha.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.OpenAiProperties;
import com.mesha.api.model.OpenAiAuthMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates AI ticket drafts using a user's own OpenAI credential.
 *
 * <p>Two paths, both returning the shared {@link AIDraftContent} shape:
 * <ul>
 *   <li>{@link OpenAiAuthMode#API_KEY} — standard Chat Completions API
 *       (billed per usage).</li>
 *   <li>{@link OpenAiAuthMode#CHATGPT_TOKEN} — the ChatGPT backend Responses
 *       endpoint used by Codex, authenticated with a subscription OAuth token
 *       (no per-token billing).</li>
 * </ul>
 *
 * <p>Note: the ChatGPT-backend endpoint/shape are OpenAI/Codex internals and may
 * change; the API-key path is the documented, stable baseline.
 */
@Component
public class OpenAiAIDraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAIDraftGenerator.class);

    private final OpenAiProperties properties;
    private final DraftPromptSupport prompts;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiAIDraftGenerator(OpenAiProperties properties,
                                  DraftPromptSupport prompts,
                                  ObjectMapper objectMapper,
                                  RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Generate a structured ticket draft ({@link AIDraftContent}) — uses the draft
     * system prompt and JSON output mode.
     */
    public AIDraftContent generate(String prompt, OpenAiCredential credential) {
        log.info("openai_ai_draft_start authMode={} prompt_length={}", credential.authMode(), prompt.length());
        String text = credential.authMode() == OpenAiAuthMode.API_KEY
                ? generateViaApiKey(prompts.systemPrompt(), prompts.userMessage(prompt), true, credential)
                : generateViaChatGptToken(prompts.systemPrompt(), prompts.userMessage(prompt), credential);
        AIDraftContent content = prompts.parseContent(text);
        log.info("openai_ai_draft_completed authMode={}", credential.authMode());
        return content;
    }

    /**
     * Raw text completion — the caller owns the entire prompt (no draft system prompt,
     * no forced JSON). Used by the mobile chat agent's prompt-driven ReAct loop.
     */
    public String complete(String prompt, OpenAiCredential credential) {
        log.info("openai_ai_complete_start authMode={} prompt_length={}", credential.authMode(), prompt.length());
        String text = credential.authMode() == OpenAiAuthMode.API_KEY
                ? generateViaApiKey(null, prompt, false, credential)
                : generateViaChatGptToken(null, prompt, credential);
        log.info("openai_ai_complete_completed authMode={}", credential.authMode());
        return text;
    }

    // --- API_KEY: standard Chat Completions -------------------------------------

    private String generateViaApiKey(String systemPrompt, String userText, boolean jsonMode, OpenAiCredential credential) {
        String model = credential.model() != null ? credential.model() : properties.getApiModel();
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userText));

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        try {
            String response = restClient.post()
                    .uri(properties.getApiBaseUrl() + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + credential.apiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned an empty completion");
            }
            return content;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("openai_api_key_call_failed error={}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI generation failed: " + e.getMessage());
        }
    }

    // --- CHATGPT_TOKEN: ChatGPT backend Responses (Codex) -----------------------

    private String generateViaChatGptToken(String systemPrompt, String userText, OpenAiCredential credential) {
        String model = credential.model() != null ? credential.model() : properties.getChatgptModel();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("instructions", systemPrompt);
        }
        body.put("input", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "input_text", "text", userText))
        )));
        body.put("stream", true);
        body.put("store", false);
        try {
            var request = restClient.post()
                    .uri(properties.getChatgptBaseUrl() + "/codex/responses")
                    .header("Authorization", "Bearer " + credential.accessToken())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("OpenAI-Beta", "responses=experimental");
            if (credential.accountId() != null && !credential.accountId().isBlank()) {
                request = request.header("chatgpt-account-id", credential.accountId());
            }
            String response = request.body(body).retrieve().body(String.class);

            String text = extractResponsesText(response);
            if (text == null || text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "ChatGPT backend returned no output text");
            }
            return text;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("openai_chatgpt_token_call_failed error={}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ChatGPT generation failed: " + e.getMessage());
        }
    }

    /**
     * Extract assistant text from a Responses API reply. Handles both the SSE
     * stream (accumulating {@code response.output_text.delta} events, or falling
     * back to a terminal {@code response.completed}/{@code response} event) and a
     * plain single JSON object if the server did not stream.
     */
    private String extractResponsesText(String response) throws Exception {
        if (response == null || response.isBlank()) return null;

        String trimmed = response.stripLeading();
        boolean looksLikeSse = trimmed.startsWith("data:") || response.contains("\ndata:");
        if (!looksLikeSse) {
            // Non-streaming single JSON object.
            return textFromResponseNode(objectMapper.readTree(response));
        }

        StringBuilder deltas = new StringBuilder();
        String terminalText = null;
        for (String line : response.split("\n")) {
            String l = line.strip();
            if (!l.startsWith("data:")) continue;
            String data = l.substring("data:".length()).strip();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            JsonNode event;
            try {
                event = objectMapper.readTree(data);
            } catch (Exception ignored) {
                continue;
            }
            String type = event.path("type").asText("");
            if (type.equals("response.output_text.delta")) {
                deltas.append(event.path("delta").asText(""));
            } else if (type.equals("response.completed") || event.has("response")) {
                String t = textFromResponseNode(event.path("response"));
                if (t != null && !t.isBlank()) terminalText = t;
            }
        }
        if (deltas.length() > 0) return deltas.toString();
        return terminalText;
    }

    /** Pull concatenated output_text out of a Responses {@code response} object. */
    private String textFromResponseNode(JsonNode responseNode) {
        if (responseNode == null || responseNode.isMissingNode() || responseNode.isNull()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : responseNode.path("output")) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    sb.append(content.path("text").asText(""));
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
