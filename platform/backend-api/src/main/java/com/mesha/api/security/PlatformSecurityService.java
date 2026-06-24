package com.mesha.api.security;

import com.mesha.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authorizes platform-wide (non-workspace) administrative actions, currently the
 * upload and management of native client releases (APKs).
 *
 * <p>Mesha has no platform-admin role in the workspace membership model, so the
 * allow-list is configured out of band via {@code app.platform.admin-emails}
 * (comma-separated). Used in {@code @PreAuthorize("@platformSecurity.isPlatformAdmin(authentication)")}.
 */
@Service("platformSecurity")
public class PlatformSecurityService {

    private final UserRepository userRepository;
    private final Set<String> adminEmails;

    public PlatformSecurityService(UserRepository userRepository,
                                   @Value("${app.platform.admin-emails:}") String adminEmailsCsv) {
        this.userRepository = userRepository;
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isPlatformAdmin(Authentication auth) {
        if (adminEmails.isEmpty()) return false;
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return false;

        // Prefer the verified email persisted on the synced user; fall back to the JWT claim.
        String clerkId = jwt.getSubject();
        String email = userRepository.findByClerkUserId(clerkId)
                .map(u -> u.getEmail())
                .orElse(jwt.getClaimAsString("email"));

        return email != null && adminEmails.contains(email.toLowerCase(Locale.ROOT));
    }
}
