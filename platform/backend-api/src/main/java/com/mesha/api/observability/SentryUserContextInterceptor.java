package com.mesha.api.observability;

import io.sentry.Sentry;
import io.sentry.protocol.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs after Spring Security authentication to attach the authenticated user's identity
 * to the Sentry scope and MDC for the duration of the request.
 */
@Component
public class SentryUserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return true;
        }

        String clerkUserId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        MDC.put("userId", clerkUserId);

        Sentry.configureScope(scope -> {
            User sentryUser = new User();
            sentryUser.setId(clerkUserId);
            if (email != null) {
                sentryUser.setEmail(email);
            }
            scope.setUser(sentryUser);
        });

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        MDC.remove("userId");
        Sentry.configureScope(scope -> scope.setUser(null));
    }
}
