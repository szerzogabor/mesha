package com.mesha.api.repository;

import com.mesha.api.model.ConnectorAgentSessionMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConnectorAgentSessionMessageRepository extends JpaRepository<ConnectorAgentSessionMessage, UUID> {

    List<ConnectorAgentSessionMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<ConnectorAgentSessionMessage> findBySessionIdAndDeliveredAtIsNullOrderByCreatedAtAsc(UUID sessionId);
}
