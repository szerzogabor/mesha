package com.mesha.api.repository;

import com.mesha.api.model.ConnectorAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorAgentRepository extends JpaRepository<ConnectorAgent, UUID> {

    List<ConnectorAgent> findByUserIdOrderByRegisteredAtDesc(UUID userId);

    List<ConnectorAgent> findByUserIdAndLastSeenAtAfter(UUID userId, Instant threshold);

    Optional<ConnectorAgent> findByUserIdAndHostnameAndExecutorType(UUID userId, String hostname, String executorType);

    Optional<ConnectorAgent> findByIdAndUserId(UUID id, UUID userId);
}
