package com.mesha.api.controller;

import com.mesha.api.dto.ConnectorTokenResponse;
import com.mesha.api.dto.ConnectorTokenValidationResponse;
import com.mesha.api.dto.RefreshConnectorTokenRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.ConnectorAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/connector/auth")
public class ConnectorAuthController {

    private final ConnectorAuthService connectorAuthService;

    public ConnectorAuthController(ConnectorAuthService connectorAuthService) {
        this.connectorAuthService = connectorAuthService;
    }

    /**
     * Exchanges the caller's authenticated Clerk session for a connector-specific
     * access/refresh token pair that the local connector CLI persists and reuses.
     */
    @PostMapping("/login")
    public ResponseEntity<ConnectorTokenResponse> login(@CurrentUser User user) {
        return ResponseEntity.ok(connectorAuthService.login(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ConnectorTokenResponse> refresh(@Valid @RequestBody RefreshConnectorTokenRequest request) {
        return ResponseEntity.ok(connectorAuthService.refresh(request.refreshToken()));
    }

    /**
     * Lets the connector confirm its current access token is still valid before
     * relying on it for future API calls, and reports how long it has left.
     */
    @GetMapping("/validate")
    public ResponseEntity<ConnectorTokenValidationResponse> validate(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String accessToken = authorization.substring(7).trim();
        long remainingSeconds = connectorAuthService.getAccessTokenRemainingSeconds(accessToken)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired access token"));
        return ResponseEntity.ok(new ConnectorTokenValidationResponse(remainingSeconds));
    }
}
