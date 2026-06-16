package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.GitHubAppProperties;
import com.mesha.api.model.GitHubWebhookEvent;
import com.mesha.api.repository.GitHubWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final GitHubAppProperties props;
    private final GitHubWebhookEventRepository eventRepo;
    private final GitHubPullRequestService prService;
    private final GitHubAppService appService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookService(GitHubAppProperties props,
                                GitHubWebhookEventRepository eventRepo,
                                GitHubPullRequestService prService,
                                GitHubAppService appService,
                                ObjectMapper objectMapper) {
        this.props = props;
        this.eventRepo = eventRepo;
        this.prService = prService;
        this.appService = appService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the HMAC-SHA256 signature from GitHub's X-Hub-Signature-256 header.
     */
    public void verifySignature(String signature, String payload) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank()) {
            log.error("GITHUB_APP_WEBHOOK_SECRET is not configured — all webhook requests are accepted without verification. This is a security risk.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Webhook secret is not configured; refusing to process unverified events");
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid webhook signature");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(props.getWebhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(computed);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook signature mismatch");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Signature verification failed: " + e.getMessage());
        }
    }

    /**
     * Persists the raw event and dispatches processing. Idempotent via deliveryId.
     */
    @Transactional
    public void ingest(String eventType, String deliveryId, String rawPayload) {
        if (eventRepo.existsByDeliveryId(deliveryId)) {
            log.debug("Duplicate webhook delivery {}, skipping", deliveryId);
            return;
        }

        GitHubWebhookEvent event = new GitHubWebhookEvent();
        event.setEventType(eventType);
        event.setDeliveryId(deliveryId);
        event.setPayload(rawPayload);
        event.setProcessed(false);
        eventRepo.save(event);

        try {
            JsonNode payload = objectMapper.readTree(rawPayload);
            dispatch(event, eventType, payload);
            event.setProcessed(true);
        } catch (Exception e) {
            log.error("Webhook processing error for delivery {}: {}", deliveryId, e.getMessage(), e);
            event.setError(e.getMessage());
        }
        eventRepo.save(event);
    }

    private void dispatch(GitHubWebhookEvent event, String eventType, JsonNode payload) {
        switch (eventType) {
            case "pull_request" -> prService.handlePullRequestEvent(payload);
            case "installation" -> handleInstallationEvent(payload);
            case "push", "issue_comment", "issues", "workflow_run", "check_suite" ->
                    log.debug("Received {} webhook event, delivery={}", eventType, event.getDeliveryId());
            default -> log.debug("Unhandled webhook event type: {}", eventType);
        }
    }

    private void handleInstallationEvent(JsonNode payload) {
        String action = payload.path("action").asText();
        long installationId = payload.path("installation").path("id").asLong();
        switch (action) {
            case "created" -> log.info("GitHub App installation created via webhook — workspace linkage handled via redirect callback installationId={}", installationId);
            case "deleted" -> appService.deleteInstallation(installationId);
            case "suspend" -> appService.markInstallationSuspended(installationId);
            case "unsuspend" -> appService.markInstallationActive(installationId);
            default -> log.debug("Unhandled installation action: {}", action);
        }
    }

}
