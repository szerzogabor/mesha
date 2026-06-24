package com.mesha.api.security;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates CI's APK publishing calls via a static bearer token instead of a
 * Clerk-issued JWT (a human admin's session token is too short-lived to use from a
 * build pipeline). Matched on the {@code relpub_} prefix, mirroring how
 * {@link ConnectorTokenAuthenticationFilter} carves out the connector's own opaque
 * token scheme.
 */
public class ReleaseUploadTokenAuthenticationFilter extends OncePerRequestFilter {

    static final String TOKEN_PREFIX = "relpub_";

    private final String expectedToken;

    public ReleaseUploadTokenAuthenticationFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && expectedToken != null && !expectedToken.isBlank()) {
            String token = header.substring(7);
            if (constantTimeEquals(token, expectedToken)) {
                SecurityContextHolder.getContext().setAuthentication(new ReleasePublisherAuthenticationToken());
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static final class ReleasePublisherAuthenticationToken extends AbstractAuthenticationToken {
        ReleasePublisherAuthenticationToken() {
            super(List.of(new SimpleGrantedAuthority("ROLE_CI_RELEASE_PUBLISHER")));
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return "ci-release-publisher";
        }
    }
}
