package com.mesha.api.repository;

import com.mesha.api.model.ConnectorAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorAgentRepository extends JpaRepository<ConnectorAgent, UUID> {

    List<ConnectorAgent> findByUserIdOrderByRegisteredAtDesc(UUID userId);

    Optional<ConnectorAgent> findByUserIdAndHostnameAndExecutorType(UUID userId, String hostname, String executorType);

    Optional<ConnectorAgent> findByIdAndUserId(UUID id, UUID userId);
}
