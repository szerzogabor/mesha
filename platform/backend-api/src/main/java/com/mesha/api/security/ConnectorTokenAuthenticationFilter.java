package com.mesha.api.security;

import com.mesha.api.service.ConnectorAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates requests carrying an opaque connector access token (issued via
 * {@code POST /api/connector/auth/login}) instead of a Clerk-issued JWT.
 */
public class ConnectorTokenAuthenticationFilter extends OncePerRequestFilter {

    private final ConnectorAuthService connectorAuthService;

    public ConnectorTokenAuthenticationFilter(ConnectorAuthService connectorAuthService) {
        this.connectorAuthService = connectorAuthService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            connectorAuthService.validateAccessToken(token)
                .ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(new ConnectorAuthenticationToken(userId)));
        }
        filterChain.doFilter(request, response);
    }

    static final class ConnectorAuthenticationToken extends AbstractAuthenticationToken {
        private final UUID userId;

        ConnectorAuthenticationToken(UUID userId) {
            super(List.of(new SimpleGrantedAuthority("ROLE_CONNECTOR")));
            this.userId = userId;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return userId;
        }
    }
}
