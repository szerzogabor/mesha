package com.mesha.api.controller;

import com.mesha.api.dto.ConnectorTokenResponse;
import com.mesha.api.dto.RefreshConnectorTokenRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.ConnectorAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * relying on it for future API calls.
     */
    @GetMapping("/validate")
    public ResponseEntity<Void> validate() {
        return ResponseEntity.ok().build();
    }
}
