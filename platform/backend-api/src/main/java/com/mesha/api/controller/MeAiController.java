package com.mesha.api.controller;

import com.mesha.api.ai.AIDraftContent;
import com.mesha.api.ai.OpenAiAIDraftGenerator;
import com.mesha.api.ai.OpenAiCredential;
import com.mesha.api.dto.MeAiCompletionResponse;
import com.mesha.api.dto.MeAiPromptRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.OpenAiConfigService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Per-user AI generation backed by the caller's own OpenAI credential
 * (see {@link OpenAiConfigService}). Used by clients — notably the mobile app —
 * that want to generate with the user's ChatGPT/OpenAI account rather than an
 * on-device model. Clerk-authenticated via {@link CurrentUser}; both endpoints
 * return 412 (from {@code resolveCredential}) when the user hasn't connected OpenAI.
 */
@RestController
@RequestMapping("/api/me/ai")
public class MeAiController {

    private static final Logger log = LoggerFactory.getLogger(MeAiController.class);

    private final OpenAiConfigService openAiConfigService;
    private final OpenAiAIDraftGenerator openAiGenerator;

    public MeAiController(OpenAiConfigService openAiConfigService,
                         OpenAiAIDraftGenerator openAiGenerator) {
        this.openAiConfigService = openAiConfigService;
        this.openAiGenerator = openAiGenerator;
    }

    /** Structured ticket draft from a natural-language prompt. */
    @PostMapping("/draft")
    public ResponseEntity<AIDraftContent> draft(
            @CurrentUser User user,
            @Valid @RequestBody MeAiPromptRequest req) {
        log.info("me_ai_draft userId={} prompt_length={}", user.getId(), req.prompt().length());
        OpenAiCredential credential = openAiConfigService.resolveCredential(user.getId());
        return ResponseEntity.ok(openAiGenerator.generate(req.prompt(), credential));
    }

    /** Raw text completion — the caller owns the full prompt. */
    @PostMapping("/complete")
    public ResponseEntity<MeAiCompletionResponse> complete(
            @CurrentUser User user,
            @Valid @RequestBody MeAiPromptRequest req) {
        log.info("me_ai_complete userId={} prompt_length={}", user.getId(), req.prompt().length());
        OpenAiCredential credential = openAiConfigService.resolveCredential(user.getId());
        return ResponseEntity.ok(new MeAiCompletionResponse(openAiGenerator.complete(req.prompt(), credential)));
    }
}
