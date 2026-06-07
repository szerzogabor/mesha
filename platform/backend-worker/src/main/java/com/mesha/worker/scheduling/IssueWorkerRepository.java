package com.mesha.worker.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IssueWorkerRepository extends JpaRepository<IssueWorkerRecord, UUID> {
}
