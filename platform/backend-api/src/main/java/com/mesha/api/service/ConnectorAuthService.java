package com.mesha.api.service;

import com.mesha.api.dto.ConnectorTokenResponse;
import com.mesha.api.model.ConnectorCredential;
import com.mesha.api.model.User;
import com.mesha.api.repository.ConnectorCredentialRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, refreshes and validates the opaque connector access/refresh token pairs
 * used by the Mesha Connector CLI to authenticate against the backend API.
 * Raw tokens are never persisted — only their SHA-256 hashes are stored.
 */
@Service
public class ConnectorAuthService {

    static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final String ACCESS_TOKEN_PREFIX = "mcat_";
    private static final String REFRESH_TOKEN_PREFIX = "mcrt_";

    private final ConnectorCredentialRepository connectorCredentialRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConnectorAuthService(ConnectorCredentialRepository connectorCredentialRepository) {
        this.connectorCredentialRepository = connectorCredentialRepository;
    }

    public ConnectorTokenResponse login(User user) {
        String accessToken = generateToken(ACCESS_TOKEN_PREFIX);
        String refreshToken = generateToken(REFRESH_TOKEN_PREFIX);
        Instant now = Instant.now();

        ConnectorCredential credential = new ConnectorCredential();
        credential.setUserId(user.getId());
        credential.setAccessTokenHash(hash(accessToken));
        credential.setAccessTokenExpiresAt(now.plus(ACCESS_TOKEN_TTL));
        credential.setRefreshTokenHash(hash(refreshToken));
        credential.setRefreshTokenExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        connectorCredentialRepository.save(credential);

        return ConnectorTokenResponse.of(accessToken, refreshToken, ACCESS_TOKEN_TTL.toSeconds());
    }

    public ConnectorTokenResponse refresh(String refreshToken) {
        ConnectorCredential credential = connectorCredentialRepository.findByRefreshTokenHash(hash(refreshToken))
            .filter(c -> c.getRefreshTokenExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        String newAccessToken = generateToken(ACCESS_TOKEN_PREFIX);
        String newRefreshToken = generateToken(REFRESH_TOKEN_PREFIX);
        Instant now = Instant.now();

        credential.setAccessTokenHash(hash(newAccessToken));
        credential.setAccessTokenExpiresAt(now.plus(ACCESS_TOKEN_TTL));
        credential.setRefreshTokenHash(hash(newRefreshToken));
        credential.setRefreshTokenExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        connectorCredentialRepository.save(credential);

        return ConnectorTokenResponse.of(newAccessToken, newRefreshToken, ACCESS_TOKEN_TTL.toSeconds());
    }

    public Optional<UUID> validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        return connectorCredentialRepository.findByAccessTokenHash(hash(accessToken))
            .filter(c -> c.getAccessTokenExpiresAt().isAfter(Instant.now()))
            .map(ConnectorCredential::getUserId);
    }

    private String generateToken(String prefix) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
