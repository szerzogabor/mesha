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
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.debug("Generating AI draft, attempt {}/{}", attempt, MAX_RETRIES);
                return provider.generateTicketDraft(prompt);
            } catch (Exception e) {
                lastException = e;
                log.warn("AI generation attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw new RuntimeException("AI generation failed after " + MAX_RETRIES + " attempts", lastException);
    }
}
