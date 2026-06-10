package com.mesha.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.service.BlocksConfigService;
import com.mesha.api.worker.blocks.BlocksAdapter;
import com.mesha.api.worker.orchestration.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Generates AI ticket drafts by delegating to the Blocks platform.
 * Creates a Blocks session with a structured prompt, polls synchronously
 * until completion, then parses the agent's JSON response into AIDraftContent.
 */
@Component
public class BlocksAIDraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(BlocksAIDraftGenerator.class);
    private static final int POLL_INTERVAL_MS = 3000;
    private static final int MAX_POLL_ATTEMPTS = 40; // ~2 minutes total

    private final BlocksAdapter blocksAdapter;
    private final BlocksConfigService blocksConfigService;
    private final ObjectMapper objectMapper;

    public BlocksAIDraftGenerator(BlocksAdapter blocksAdapter,
                                   BlocksConfigService blocksConfigService,
                                   ObjectMapper objectMapper) {
        this.blocksAdapter = blocksAdapter;
        this.blocksConfigService = blocksConfigService;
        this.objectMapper = objectMapper;
    }

    public AIDraftContent generate(String prompt, UUID workspaceId) {
        String apiKey = blocksConfigService.getApiKey(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "Blocks is not configured for this workspace"));

        String message = buildDraftMessage(prompt);
        log.info("blocks_ai_draft_start workspace_id={} prompt_length={}", workspaceId, prompt.length());

        String sessionId = blocksAdapter.createDraftSession(message, apiKey);
        log.info("blocks_ai_draft_session_created workspace_id={} session_id={}", workspaceId, sessionId);

        return pollForResult(sessionId, workspaceId);
    }

    private AIDraftContent pollForResult(String sessionId, UUID workspaceId) {
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Draft generation interrupted");
            }

            SessionResult result = blocksAdapter.pollSession(sessionId);
            log.debug("blocks_ai_draft_poll workspace_id={} session_id={} status={} attempt={}/{}",
                    workspaceId, sessionId, result.status(), attempt, MAX_POLL_ATTEMPTS);

            switch (result.status()) {
                case COMPLETED -> {
                    log.info("blocks_ai_draft_completed workspace_id={} session_id={} attempts={}",
                            workspaceId, sessionId, attempt);
                    return parseResponse(result.finalMessage(), sessionId);
                }
                case FAILED -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Blocks AI session failed to generate draft");
                default -> { /* PENDING or RUNNING — keep polling */ }
            }
        }

        throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                "Blocks AI session timed out while generating draft");
    }

    private AIDraftContent parseResponse(String responseText, String sessionId) {
        if (responseText == null || responseText.isBlank()) {
            List<String> messages = blocksAdapter.fetchAssistantMessages(sessionId);
            if (messages != null && !messages.isEmpty()) {
                responseText = messages.get(messages.size() - 1);
            }
        }

        if (responseText == null || responseText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Blocks AI returned empty draft response");
        }

        try {
            String json = responseText.trim();
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
            log.error("blocks_ai_draft_parse_failed session_id={} error={}", sessionId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse Blocks AI draft response");
        }
    }

    private String buildDraftMessage(String userPrompt) {
        return """
                IMPORTANT: This is a TEXT GENERATION task. Do NOT use any tools. Do NOT call any APIs. \
                Do NOT create tickets in Linear, Jira, GitHub, or any other system. \
                Do NOT take any action in any external service. \
                Your ONLY job is to write a JSON object to your final message and stop.

                You are a senior technical product manager. Read the user's request below and produce a \
                structured issue ticket in JSON format.

                Output ONLY the raw JSON object — no markdown fences, no explanation, no preamble, no tool calls.
                The JSON must contain exactly these fields:
                - title: concise issue title (max 150 characters)
                - description: 2-4 sentence technical summary of the issue/feature
                - acceptanceCriteria: acceptance criteria in markdown bullet format (each criterion on its own line starting with "- ")
                - suggestedLabels: JSON array of 1-4 relevant label strings (e.g. ["backend", "api", "auth"])
                - prioritySuggestion: one of URGENT, HIGH, MEDIUM, LOW
                - implementationNotes: technical guidance for implementation (2-4 sentences)
                - scopeNotes: what is explicitly in scope (markdown bullet list)
                - outOfScopeNotes: what is explicitly out of scope (markdown bullet list)

                User request:

                """ + userPrompt;
    }
}
