package com.mesha.api.controller;

import com.mesha.api.service.BlocksWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks/blocks")
public class BlocksWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BlocksWebhookController.class);

    private final BlocksWebhookService webhookService;

    public BlocksWebhookController(BlocksWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Blocks-Event", required = false, defaultValue = "unknown") String event,
            @RequestHeader(value = "X-Blocks-Delivery", required = false, defaultValue = "") String deliveryId,
            @RequestHeader(value = "X-Blocks-Signature", required = false) String signature,
            @RequestBody String payload) {

        log.debug("Received Blocks webhook event={} delivery={}", event, deliveryId);
        webhookService.verifySignature(signature, payload);
        UUID eventId = webhookService.persist(event, deliveryId, payload);
        if (eventId != null) {
            webhookService.processAsync(eventId);
        }
        return ResponseEntity.ok().build();
    }
}
