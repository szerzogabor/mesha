package com.mesha.api.repository;

import com.mesha.api.model.AIDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AIDraftRepository extends JpaRepository<AIDraft, UUID> {
}
