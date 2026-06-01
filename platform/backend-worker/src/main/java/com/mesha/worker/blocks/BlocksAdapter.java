package com.mesha.worker.blocks;

import com.mesha.worker.observability.WorkflowTracer;
import com.mesha.worker.orchestration.ProviderAdapter;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Blocks AI platform adapter. HTTP calls (when implemented) are auto-instrumented by the
 * OpenTelemetry Java Agent; WorkflowTracer handles structured logging and error capture.
 */
@Component
public class BlocksAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(BlocksAdapter.class);

    private final WorkflowTracer workflowTracer;

    public BlocksAdapter(WorkflowTracer workflowTracer) {
        this.workflowTracer = workflowTracer;
    }

    @Override
    public String providerName() {
        return "blocks";
    }

    @Override
    public SessionResult createSession(SessionRequest request) {
        String localSessionId = UUID.randomUUID().toString();
        MDC.put("sessionId", localSessionId);
        MDC.put("provider", providerName());

        log.info("session_create_start provider={} issue_id={} session_id={}",
                providerName(), request.issueId(), localSessionId);

        try {
            // TODO: implement Blocks API session creation
            throw new UnsupportedOperationException("Blocks session creation not yet implemented");
        } catch (Exception e) {
            workflowTracer.captureAiProviderFailure(providerName(), "createSession", 0, e);
            throw e;
        } finally {
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }

    @Override
    public SessionResult pollSession(String providerSessionId) {
        MDC.put("sessionId", providerSessionId);
        MDC.put("provider", providerName());

        log.info("session_poll_start provider={} session_id={}", providerName(), providerSessionId);

        try {
            // TODO: implement Blocks API session polling (final_message)
            throw new UnsupportedOperationException("Blocks session polling not yet implemented");
        } catch (Exception e) {
            workflowTracer.capturePollingFailure(providerName(), providerSessionId, 1, e);
            throw e;
        } finally {
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }
}
