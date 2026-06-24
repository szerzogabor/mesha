package com.mesha.api.security;

import com.mesha.api.service.ConnectorAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${clerk.jwks-uri}")
    private String jwksUri;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Authenticates the opaque connector access token (issued via the Clerk-protected
     * /api/connector/auth/login exchange) instead of a Clerk JWT. Matched by the
     * "Bearer mcat_" prefix rather than a fixed path so any current or future endpoint
     * called by the connector is routed here instead of the Clerk JWT chain. Must be
     * ordered before the default chain so this narrower match takes precedence.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain connectorFilterChain(HttpSecurity http, ConnectorAuthService connectorAuthService) throws Exception {
        return http
            .securityMatcher(request -> {
                String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
                return auth != null && auth.startsWith("Bearer mcat_");
            })
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new ConnectorTokenAuthenticationFilter(connectorAuthService), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(eh -> eh.authenticationEntryPoint(
                (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            ))
            .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                // Public client-release distribution: marketing site + in-app update checks.
                // Admin endpoints (/api/releases/admin/**, POST/PATCH/DELETE) remain authenticated
                // and are additionally guarded by @platformSecurity.isPlatformAdmin.
                .requestMatchers(HttpMethod.GET,
                        "/api/releases/*",
                        "/api/releases/*/latest",
                        "/api/releases/*/latest/download",
                        "/api/releases/*/download").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/github/webhooks").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/webhooks/blocks").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/connector/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/sync").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder()))
            )
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
