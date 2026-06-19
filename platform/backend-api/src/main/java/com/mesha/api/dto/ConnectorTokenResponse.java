package com.mesha.api.dto;

public record ConnectorTokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType
) {
    public static ConnectorTokenResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
        return new ConnectorTokenResponse(accessToken, refreshToken, expiresInSeconds, "Bearer");
    }
}
