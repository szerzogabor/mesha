package com.mesha.worker.blocks;

import com.mesha.worker.observability.WorkflowTracer;
import com.mesha.worker.orchestration.ProviderAdapter;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

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

        ITransaction tx = Sentry.startTransaction("blocks.createSession", "ai.session.create");
        tx.setTag("provider", providerName());
        tx.setTag("issue.id", request.issueId());
        tx.setData("issue.title", request.issueTitle());

        log.info("session_create_start provider={} issue_id={} session_id={}",
                providerName(), request.issueId(), localSessionId);

        try {
            // TODO: implement Blocks API session creation
            throw new UnsupportedOperationException("Blocks session creation not yet implemented");
        } catch (Exception e) {
            tx.setStatus(SpanStatus.INTERNAL_ERROR);
            tx.setThrowable(e);
            workflowTracer.captureAiProviderFailure(providerName(), "createSession", 0, e);
            throw e;
        } finally {
            tx.finish();
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }

    @Override
    public SessionResult pollSession(String providerSessionId) {
        MDC.put("sessionId", providerSessionId);
        MDC.put("provider", providerName());

        // Attach to an existing transaction if one is active, otherwise start a new one
        ISpan span;
        ISpan active = Sentry.getSpan();
        if (active != null && !active.isFinished()) {
            span = active.startChild("blocks.pollSession", "ai.session.poll");
        } else {
            ITransaction tx = Sentry.startTransaction("blocks.pollSession", "ai.session.poll");
            tx.setTag("provider", providerName());
            span = tx;
        }
        span.setData("session.id", providerSessionId);

        log.info("session_poll_start provider={} session_id={}", providerName(), providerSessionId);

        try {
            // TODO: implement Blocks API session polling (final_message)
            throw new UnsupportedOperationException("Blocks session polling not yet implemented");
        } catch (Exception e) {
            span.setStatus(SpanStatus.INTERNAL_ERROR);
            span.setThrowable(e);
            workflowTracer.capturePollingFailure(providerName(), providerSessionId, 1, e);
            throw e;
        } finally {
            span.finish();
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }
}
