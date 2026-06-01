package com.mesha.worker.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationFilterTest {

    private final CorrelationFilter filter = new CorrelationFilter();

    @AfterEach
    void cleanUpMdc() {
        MDC.clear();
    }

    @Test
    void generatesCorrelationIdWhenHeaderAbsent() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get("correlationId")).isNotNull().isNotBlank()
        );
    }

    @Test
    void usesProvidedCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "my-correlation-id");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get("correlationId")).isEqualTo("my-correlation-id")
        );
    }

    @Test
    void generatesRequestIdWhenHeaderAbsent() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get("requestId")).isNotNull().isNotBlank()
        );
    }

    @Test
    void usesProvidedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "req-abc");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get("requestId")).isEqualTo("req-abc")
        );
    }

    @Test
    void setsInstallationIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Installation-ID", "install-999");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get("installationId")).isEqualTo("install-999")
        );
    }

    @Test
    void doesNotSetInstallationIdWhenHeaderAbsent() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get("installationId")).isNull()
        );
    }

    @Test
    void clearsMdcAfterRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "c1");
        request.addHeader("X-Request-ID", "r1");
        request.addHeader("X-Installation-ID", "i1");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void clearsMdcEvenWhenFilterChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            filter.doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> { throw new RuntimeException("chain error"); });
        } catch (RuntimeException ignored) {
        }
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }
}
