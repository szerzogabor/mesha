package com.mesha.api.repository;

import com.mesha.api.model.ConnectorCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorCredentialRepository extends JpaRepository<ConnectorCredential, UUID> {
    Optional<ConnectorCredential> findByAccessTokenHash(String accessTokenHash);
    Optional<ConnectorCredential> findByRefreshTokenHash(String refreshTokenHash);
}
