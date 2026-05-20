package com.mesha.worker.config;

import io.sentry.Sentry;
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
 * Servlet filter that establishes a correlation ID for every inbound request.
 * The ID is propagated through MDC (for structured logs) and Sentry scope (for trace correlation).
 * Downstream components can inject additional MDC keys (workflowId, sessionId, jobId, etc.)
 * without needing to manage the base correlation ID themselves.
 */
@Component
@Order(1)
public class CorrelationFilter implements Filter {

    static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_CORRELATION_ID, correlationId);

        final String id = correlationId;
        Sentry.configureScope(scope -> scope.setTag(MDC_CORRELATION_ID, id));

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            Sentry.configureScope(scope -> scope.removeTag(MDC_CORRELATION_ID));
        }
    }
}
