package com.mesha.api.repository;

import com.mesha.api.model.BlocksMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BlocksMessageRepository extends JpaRepository<BlocksMessage, UUID> {

    @Query("SELECT m FROM BlocksMessage m WHERE m.session.id = :sessionId ORDER BY m.createdAt ASC")
    List<BlocksMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    long countBySessionId(UUID sessionId);

    @Modifying
    @Query("DELETE FROM BlocksMessage m WHERE m.session.id = :sessionId")
    void deleteAllBySessionId(UUID sessionId);
}
