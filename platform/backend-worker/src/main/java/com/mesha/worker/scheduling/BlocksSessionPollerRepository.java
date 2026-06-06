package com.mesha.worker.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BlocksSessionPollerRepository extends JpaRepository<BlocksSessionRecord, UUID> {

    @Query("SELECT s FROM BlocksSessionRecord s WHERE s.executionState NOT IN :states")
    List<BlocksSessionRecord> findAllByExecutionStateNotIn(@Param("states") Collection<AIExecutionState> states);
}
