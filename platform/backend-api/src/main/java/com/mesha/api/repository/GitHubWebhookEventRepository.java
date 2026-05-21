package com.mesha.api.repository;

import com.mesha.api.model.GitHubWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GitHubWebhookEventRepository extends JpaRepository<GitHubWebhookEvent, UUID> {
    Optional<GitHubWebhookEvent> findByDeliveryId(String deliveryId);
    boolean existsByDeliveryId(String deliveryId);
}
