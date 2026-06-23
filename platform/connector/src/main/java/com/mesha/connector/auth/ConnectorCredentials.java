package com.mesha.connector.auth;

import java.time.Instant;

public record ConnectorCredentials(
        String accessToken,
        Instant accessTokenExpiresAt
) {}
