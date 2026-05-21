package com.mesha.api.controller;

import com.mesha.api.service.GitHubWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github/webhooks")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubWebhookService webhookService;

    public GitHubWebhookController(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false, defaultValue = "unknown") String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false, defaultValue = "") String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        log.debug("Received GitHub webhook event={} delivery={}", event, deliveryId);
        webhookService.verifySignature(signature, payload);
        webhookService.ingest(event, deliveryId, payload);
        return ResponseEntity.ok().build();
    }
}
