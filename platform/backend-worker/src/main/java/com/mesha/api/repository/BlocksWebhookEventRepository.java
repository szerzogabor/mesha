package com.mesha.api.repository;

import com.mesha.api.model.BlocksWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlocksWebhookEventRepository extends JpaRepository<BlocksWebhookEvent, UUID> {
    boolean existsByDeliveryId(String deliveryId);
}
