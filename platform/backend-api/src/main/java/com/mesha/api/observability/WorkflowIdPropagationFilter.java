package com.mesha.api.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts the X-Workflow-ID header from inbound requests and writes it to MDC so that every
 * log line emitted within that request automatically carries the workflowId field. This enables
 * end-to-end correlation of AI orchestration, issue workflow, and webhook processing events
 * across frontend and backend log streams in Loki and Grafana.
 *
 * The header is forwarded in the response so callers can confirm propagation.
 */
@Component
@Order(1)
public class WorkflowIdPropagationFilter extends OncePerRequestFilter {

    static final String WORKFLOW_ID_HEADER = "X-Workflow-ID";
    static final String WORKFLOW_ID_MDC_KEY = "workflowId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String workflowId = request.getHeader(WORKFLOW_ID_HEADER);
        if (workflowId != null && !workflowId.isBlank()) {
            MDC.put(WORKFLOW_ID_MDC_KEY, workflowId);
            response.setHeader(WORKFLOW_ID_HEADER, workflowId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(WORKFLOW_ID_MDC_KEY);
        }
    }
}
