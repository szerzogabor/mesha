package com.mesha.api.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC with per-request context so every log line emitted during the request carries
 * correlation fields automatically. Also returns the correlation ID in a response header.
 *
 * MDC keys set here:
 *   correlationId  — client-supplied or newly generated UUID (also returned in X-Correlation-ID)
 *   requestId      — always-fresh UUID identifying this specific server-side request
 *   workflowId     — forwarded from X-Workflow-ID header (set by WorkflowIdPropagationFilter)
 *   requestMethod  — HTTP verb
 *   requestUri     — path with UUID segments replaced by {id} for low cardinality
 *   userAgent      — User-Agent header value
 *
 * OTel trace context (trace_id, span_id, trace_flags) is injected into MDC automatically
 * by the OpenTelemetry Java Agent's Logback bridge — no manual extraction needed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    static final String CORRELATION_ID_MDC_KEY = "correlationId";
    static final String REQUEST_ID_MDC_KEY = "requestId";
    static final String REQUEST_METHOD_MDC_KEY = "requestMethod";
    static final String REQUEST_URI_MDC_KEY = "requestUri";
    static final String USER_AGENT_MDC_KEY = "userAgent";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        String correlationId = resolveCorrelationId(request);
        String requestId = UUID.randomUUID().toString();
        String sanitizedUri = sanitizeUri(request);

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(REQUEST_METHOD_MDC_KEY, request.getMethod());
        MDC.put(REQUEST_URI_MDC_KEY, sanitizedUri);
        MDC.put(USER_AGENT_MDC_KEY, request.getHeader("User-Agent"));

        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String msg = "request completed status={} durationMs={}";
            if (status >= 500) {
                log.error(msg, status, durationMs);
            } else if (status == 401 || status == 403) {
                log.debug(msg, status, durationMs);
            } else if (status >= 400) {
                log.warn(msg, status, durationMs);
            } else {
                log.info(msg, status, durationMs);
            }
            MDC.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        return (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
    }

    private String sanitizeUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{id}");
    }
}
