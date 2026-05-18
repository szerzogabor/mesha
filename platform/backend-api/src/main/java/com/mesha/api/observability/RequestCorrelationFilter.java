package com.mesha.api.observability;

import io.sentry.Sentry;
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
 * Populates MDC with per-request context (correlationId, traceId, method, URI, user-agent)
 * so every log line emitted during the request carries these fields automatically.
 * Also propagates the correlation ID to Sentry scope and response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    static final String CORRELATION_ID_MDC_KEY = "correlationId";
    static final String TRACE_ID_MDC_KEY = "traceId";
    static final String REQUEST_METHOD_MDC_KEY = "requestMethod";
    static final String REQUEST_URI_MDC_KEY = "requestUri";
    static final String USER_AGENT_MDC_KEY = "userAgent";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        String correlationId = resolveCorrelationId(request);
        String sentryTraceId = Sentry.getSpan() != null
                ? Sentry.getSpan().getSpanContext().getTraceId().toString()
                : UUID.randomUUID().toString().replace("-", "");

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(TRACE_ID_MDC_KEY, sentryTraceId);
        MDC.put(REQUEST_METHOD_MDC_KEY, request.getMethod());
        MDC.put(REQUEST_URI_MDC_KEY, sanitizeUri(request));
        MDC.put(USER_AGENT_MDC_KEY, request.getHeader("User-Agent"));

        Sentry.configureScope(scope -> {
            scope.setTag("correlationId", correlationId);
            scope.setTag("httpMethod", request.getMethod());
            scope.setTag("httpPath", sanitizeUri(request));
        });

        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.debug("request completed status={} durationMs={}", response.getStatus(), durationMs);
            MDC.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        return (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
    }

    private String sanitizeUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Strip UUIDs from path to avoid high-cardinality Sentry tags
        return uri.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{id}");
    }
}
