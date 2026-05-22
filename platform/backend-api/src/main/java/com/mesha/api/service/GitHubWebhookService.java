package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.GitHubAppProperties;
import com.mesha.api.model.GitHubWebhookEvent;
import com.mesha.api.repository.GitHubWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
            log.warn("webhook_signature_invalid reason=missing_or_wrong_prefix");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid webhook signature");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(props.getWebhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(computed);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("webhook_signature_mismatch");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook signature mismatch");
            }
            log.debug("webhook_signature_verified");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("webhook_signature_error", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Signature verification failed: " + e.getMessage());
        }
    }

    /**
     * Persists the raw event and dispatches processing. Idempotent via deliveryId.
     */
    @Transactional
    public void ingest(String eventType, String deliveryId, String rawPayload) {
        MDC.put("githubEventId", deliveryId);
        MDC.put("githubEventType", eventType);
        long startMs = System.currentTimeMillis();
        try {
            if (eventRepo.existsByDeliveryId(deliveryId)) {
                log.debug("webhook_duplicate delivery_id={} event_type={} action=skipped", deliveryId, eventType);
                return;
            }

            log.info("webhook_ingest_start delivery_id={} event_type={}", deliveryId, eventType);

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
                long durationMs = System.currentTimeMillis() - startMs;
                log.info("webhook_ingest_complete delivery_id={} event_type={} duration_ms={}",
                        deliveryId, eventType, durationMs);
            } catch (Exception e) {
                log.error("webhook_processing_error delivery_id={} event_type={} error={}",
                        deliveryId, eventType, e.getMessage(), e);
                event.setError(e.getMessage());
            }
            eventRepo.save(event);
        } finally {
            MDC.remove("githubEventId");
            MDC.remove("githubEventType");
        }
    }

    private void dispatch(GitHubWebhookEvent event, String eventType, JsonNode payload) {
        String action = payload.path("action").asText(null);
        switch (eventType) {
            case "pull_request" -> {
                int prNumber = payload.path("pull_request").path("number").asInt(0);
                String repo = payload.path("repository").path("full_name").asText("unknown");
                log.info("webhook_dispatch event_type=pull_request action={} pr_number={} repo={}",
                        action, prNumber, repo);
                prService.handlePullRequestEvent(payload);
            }
            case "installation" -> {
                long installationId = payload.path("installation").path("id").asLong();
                log.info("webhook_dispatch event_type=installation action={} installation_id={}",
                        action, installationId);
                handleInstallationEvent(payload);
            }
            case "push" -> {
                String ref = payload.path("ref").asText("unknown");
                String repo = payload.path("repository").path("full_name").asText("unknown");
                log.debug("webhook_dispatch event_type=push ref={} repo={} delivery={}",
                        ref, repo, event.getDeliveryId());
            }
            case "issue_comment", "issues", "workflow_run", "check_suite" ->
                    log.debug("webhook_dispatch event_type={} action={} delivery={}",
                            eventType, action, event.getDeliveryId());
            default ->
                    log.debug("webhook_dispatch_unhandled event_type={} delivery={}", eventType, event.getDeliveryId());
        }
    }

    private void handleInstallationEvent(JsonNode payload) {
        String action = payload.path("action").asText();
        long installationId = payload.path("installation").path("id").asLong();
        String accountLogin = payload.path("installation").path("account").path("login").asText("unknown");
        log.info("installation_event action={} installation_id={} account={}", action, installationId, accountLogin);
        switch (action) {
            case "deleted" -> {
                appService.markInstallationDeleted(installationId);
                log.info("installation_deleted installation_id={} account={}", installationId, accountLogin);
            }
            case "suspend" -> {
                appService.markInstallationSuspended(installationId);
                log.info("installation_suspended installation_id={} account={}", installationId, accountLogin);
            }
            case "unsuspend" ->
                    log.info("installation_unsuspend installation_id={} account={}", installationId, accountLogin);
            default ->
                    log.debug("installation_action_unhandled action={} installation_id={}", action, installationId);
        }
    }
}
