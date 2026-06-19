package com.mesha.api.security;

import com.mesha.api.service.ConnectorAuthService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ConnectorTokenAuthenticationFilterTest {

    private final ConnectorAuthService connectorAuthService = Mockito.mock(ConnectorAuthService.class);
    private final ConnectorTokenAuthenticationFilter filter = new ConnectorTokenAuthenticationFilter(connectorAuthService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_setsAuthenticationOnContext() throws Exception {
        UUID userId = UUID.randomUUID();
        when(connectorAuthService.validateAccessToken("mcat_valid")).thenReturn(Optional.of(userId));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer mcat_valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        Mockito.verify(chain).doFilter(request, response);
    }

    @Test
    void invalidToken_leavesContextUnauthenticated() throws Exception {
        when(connectorAuthService.validateAccessToken("mcat_invalid")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer mcat_invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(chain).doFilter(request, response);
    }

    @Test
    void missingHeader_leavesContextUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(chain).doFilter(request, response);
    }
}
