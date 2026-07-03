package com.mesha.api.repository;

import com.mesha.api.model.UserOpenAiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOpenAiConfigRepository extends JpaRepository<UserOpenAiConfig, UUID> {

    Optional<UserOpenAiConfig> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
