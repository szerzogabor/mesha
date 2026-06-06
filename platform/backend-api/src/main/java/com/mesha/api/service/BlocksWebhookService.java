package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.config.BlocksWebhookProperties;
import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.BlocksWebhookEvent;
import com.mesha.api.repository.BlocksWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class BlocksWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BlocksWebhookService.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final BlocksWebhookProperties props;
    private final BlocksWebhookEventRepository eventRepo;
    private final BlocksSessionService blocksSessionService;
    private final ObjectMapper objectMapper;

    public BlocksWebhookService(BlocksWebhookProperties props,
                                BlocksWebhookEventRepository eventRepo,
                                BlocksSessionService blocksSessionService,
                                ObjectMapper objectMapper) {
        this.props = props;
        this.eventRepo = eventRepo;
        this.blocksSessionService = blocksSessionService;
        this.objectMapper = objectMapper;
    }

    public void verifySignature(String signature, String payload) {
        if (props.getSecret() == null || props.getSecret().isBlank()) {
            log.error("BLOCKS_WEBHOOK_SECRET is not configured — all webhook requests are rejected. This is a security risk.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Blocks webhook secret is not configured; refusing to process unverified events");
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Blocks webhook signature");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(props.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(computed);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Blocks webhook signature mismatch");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Signature verification failed: " + e.getMessage());
        }
    }

    /**
     * Persists the raw event record and returns its ID for async processing.
     * Idempotent via deliveryId — duplicate deliveries return null.
     */
    @Transactional
    public UUID persist(String eventType, String deliveryId, String rawPayload) {
        if (eventRepo.existsByDeliveryId(deliveryId)) {
            log.debug("Duplicate Blocks webhook delivery {}, skipping", deliveryId);
            return null;
        }
        BlocksWebhookEvent event = new BlocksWebhookEvent();
        event.setEventType(eventType);
        event.setDeliveryId(deliveryId);
        event.setPayload(rawPayload);
        event.setProcessed(false);
        event = eventRepo.save(event);
        log.debug("Persisted Blocks webhook event type={} delivery={} id={}", eventType, deliveryId, event.getId());
        return event.getId();
    }

    /**
     * Processes a previously persisted webhook event asynchronously.
     * Runs in a separate thread so the HTTP response is already sent by the time this executes.
     */
    @Async
    @Transactional
    public void processAsync(UUID eventId) {
        BlocksWebhookEvent event = eventRepo.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("Blocks webhook event {} not found for async processing", eventId);
            return;
        }
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            dispatch(event.getEventType(), payload);
            event.setProcessed(true);
        } catch (Exception e) {
            log.error("Blocks webhook processing error for delivery {}: {}", event.getDeliveryId(), e.getMessage(), e);
            event.setError(e.getMessage());
        }
        eventRepo.save(event);
    }

    private void dispatch(String eventType, JsonNode payload) {
        String providerSessionId = payload.path("providerSessionId").asText(null);
        if (providerSessionId == null || providerSessionId.isBlank()) {
            log.warn("Blocks webhook event '{}' missing providerSessionId, skipping dispatch", eventType);
            return;
        }

        switch (eventType) {
            case "session.state_changed", "execution.completed", "execution.failed" -> {
                String stateRaw = payload.path("state").asText(null);
                if (stateRaw == null) {
                    log.warn("Blocks webhook event '{}' missing state field", eventType);
                    return;
                }
                AIExecutionState newState;
                try {
                    newState = AIExecutionState.valueOf(stateRaw);
                } catch (IllegalArgumentException e) {
                    log.warn("Blocks webhook event '{}' unknown state '{}', skipping", eventType, stateRaw);
                    return;
                }
                String prUrl       = payload.path("prUrl").asText(null);
                Integer prNumber   = payload.hasNonNull("prNumber") ? payload.path("prNumber").asInt() : null;
                String branchName  = payload.path("branchName").asText(null);
                String errorMessage = payload.path("errorMessage").asText(null);
                blocksSessionService.handleWebhookStateUpdate(providerSessionId, newState, prUrl, prNumber, branchName, errorMessage);
            }
            case "pr.created" -> {
                String prUrl      = payload.path("prUrl").asText(null);
                Integer prNumber  = payload.hasNonNull("prNumber") ? payload.path("prNumber").asInt() : null;
                String branchName = payload.path("branchName").asText(null);
                blocksSessionService.handleWebhookStateUpdate(providerSessionId, AIExecutionState.PR_OPENED, prUrl, prNumber, branchName, null);
            }
            default -> log.debug("Unhandled Blocks webhook event type: {}", eventType);
        }
    }
}
