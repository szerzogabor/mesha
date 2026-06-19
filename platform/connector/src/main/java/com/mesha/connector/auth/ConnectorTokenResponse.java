package com.mesha.connector.auth;

record ConnectorTokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType) {}
