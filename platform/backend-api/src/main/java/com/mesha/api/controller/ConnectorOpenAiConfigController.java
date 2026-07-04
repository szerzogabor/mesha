package com.mesha.api.controller;

import com.mesha.api.dto.OpenAiConfigDto;
import com.mesha.api.dto.SaveOpenAiConfigRequest;
import com.mesha.api.security.ConnectorUserId;
import com.mesha.api.service.OpenAiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Connector-authenticated twin of {@code /api/me/openai/config}. The local
 * "Sign in with ChatGPT" helper runs the OAuth flow, then pushes the resulting
 * tokens here using a connector access token (the Clerk-only {@code /api/me/...}
 * endpoint can't accept a connector-token principal). Auth is handled by the
 * {@code Bearer mcat_} security chain; {@link ConnectorUserId} resolves the user.
 */
@RestController
@RequestMapping("/api/connector/openai/config")
public class ConnectorOpenAiConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConnectorOpenAiConfigController.class);

    private final OpenAiConfigService openAiConfigService;

    public ConnectorOpenAiConfigController(OpenAiConfigService openAiConfigService) {
        this.openAiConfigService = openAiConfigService;
    }

    @PutMapping
    public ResponseEntity<OpenAiConfigDto> save(
            @ConnectorUserId UUID userId,
            @RequestBody SaveOpenAiConfigRequest request) {
        log.info("Connector saving OpenAI config userId={} authMode={}", userId, request.authMode());
        return ResponseEntity.ok(openAiConfigService.saveConfig(userId, request));
    }
}
