package com.mesha.worker.config;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes correlation IDs for every inbound request.
 * Propagates through MDC for structured log correlation in Loki.
 *
 * MDC keys populated:
 *   correlationId   — from X-Correlation-ID header, or a fresh UUID
 *   requestId       — from X-Request-ID header, or a fresh UUID
 *   installationId  — from X-Installation-ID header (GitHub App installation)
 *   traceId         — from the active OTel span (set by the Java agent before this filter runs)
 */
@Component
@Order(1)
public class CorrelationFilter implements Filter {

    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String INSTALLATION_ID_HEADER = "X-Installation-ID";

    static final String MDC_CORRELATION_ID = "correlationId";
    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_INSTALLATION_ID = "installationId";
    static final String MDC_TRACE_ID = "traceId";

    private static final String OTEL_INVALID_TRACE_ID = "00000000000000000000000000000000";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String installationId = httpRequest.getHeader(INSTALLATION_ID_HEADER);

        // Capture the OTel traceId injected by the Java agent before this filter runs.
        String traceId = Span.current().getSpanContext().getTraceId();

        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_REQUEST_ID, requestId);
        if (installationId != null && !installationId.isBlank()) {
            MDC.put(MDC_INSTALLATION_ID, installationId);
        }
        if (!OTEL_INVALID_TRACE_ID.equals(traceId)) {
            MDC.put(MDC_TRACE_ID, traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_INSTALLATION_ID);
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
