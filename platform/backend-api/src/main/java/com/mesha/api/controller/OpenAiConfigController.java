package com.mesha.api.controller;

import com.mesha.api.dto.OpenAiConfigDto;
import com.mesha.api.dto.SaveOpenAiConfigRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.OpenAiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Per-user OpenAI credential management. Unlike Blocks (per-workspace, admin-gated),
 * a ChatGPT subscription is personal, so these endpoints are scoped to the
 * authenticated user and require no workspace role.
 */
@RestController
@RequestMapping("/api/me/openai/config")
public class OpenAiConfigController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConfigController.class);

    private final OpenAiConfigService openAiConfigService;

    public OpenAiConfigController(OpenAiConfigService openAiConfigService) {
        this.openAiConfigService = openAiConfigService;
    }

    @GetMapping
    public ResponseEntity<OpenAiConfigDto> getConfig(@CurrentUser User user) {
        return openAiConfigService.getConfig(user.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<OpenAiConfigDto> saveConfig(
            @CurrentUser User user,
            @RequestBody SaveOpenAiConfigRequest request) {
        log.info("Saving OpenAI config userId={} authMode={}", user.getId(), request.authMode());
        return ResponseEntity.ok(openAiConfigService.saveConfig(user, request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteConfig(@CurrentUser User user) {
        log.info("Deleting OpenAI config userId={}", user.getId());
        openAiConfigService.disconnect(user.getId());
        return ResponseEntity.noContent().build();
    }
}
