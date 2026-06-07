package com.mesha.worker.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlocksMessageWorkerRepository extends JpaRepository<BlocksMessageRecord, UUID> {
}
