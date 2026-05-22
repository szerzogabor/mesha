package com.mesha.api.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AIOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrationService.class);
    private static final int MAX_RETRIES = 2;

    private final AIProvider provider;

    public AIOrchestrationService(AIProvider provider) {
        this.provider = provider;
    }

    public AIDraftContent generateDraft(String prompt) {
        log.info("ai_draft_generate_start provider={} prompt_length={}", provider.getClass().getSimpleName(), prompt.length());
        long startMs = System.currentTimeMillis();
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.debug("ai_draft_attempt provider={} attempt={} max_attempts={}",
                        provider.getClass().getSimpleName(), attempt, MAX_RETRIES);
                AIDraftContent result = provider.generateTicketDraft(prompt);
                long durationMs = System.currentTimeMillis() - startMs;
                log.info("ai_draft_generate_complete provider={} attempt={} duration_ms={}",
                        provider.getClass().getSimpleName(), attempt, durationMs);
                return result;
            } catch (Exception e) {
                lastException = e;
                long elapsedMs = System.currentTimeMillis() - startMs;
                log.warn("ai_draft_attempt_failed provider={} attempt={} max_attempts={} elapsed_ms={} error={}",
                        provider.getClass().getSimpleName(), attempt, MAX_RETRIES, elapsedMs, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    long waitMs = 1000L * attempt;
                    log.debug("ai_draft_retry_wait attempt={} wait_ms={}", attempt, waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("ai_draft_interrupted attempt={}", attempt);
                        break;
                    }
                }
            }
        }

        long totalDurationMs = System.currentTimeMillis() - startMs;
        log.error("ai_draft_generate_failed provider={} attempts={} total_duration_ms={}",
                provider.getClass().getSimpleName(), MAX_RETRIES, totalDurationMs, lastException);
        throw new RuntimeException("AI generation failed after " + MAX_RETRIES + " attempts", lastException);
    }
}
