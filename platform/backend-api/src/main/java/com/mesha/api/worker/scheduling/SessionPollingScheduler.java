package com.mesha.api.worker.scheduling;

import com.mesha.api.model.BlocksSession;
import com.mesha.api.repository.BlocksSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Scheduled job that drives BlocksSession state transitions forward.
 * Runs every {@code mesha.polling.interval-ms} milliseconds (default 5 s) using
 * a fixed delay so runs never overlap within a single instance.
 *
 * <p>Idempotency across multiple instances is guaranteed by a Redis-backed
 * distributed lock ({@link SessionPollService#acquireBackoffLock}) that also
 * doubles as the exponential-backoff gate: the lock TTL equals the computed
 * backoff interval, so no two workers can poll the same session before that
 * interval elapses.
 *
 * <p>This scheduler runs within the backend-api process. The backend-worker module
 * retains an equivalent implementation for future extraction back into a standalone service.
 */
@Component
public class SessionPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionPollingScheduler.class);

    private final BlocksSessionRepository sessionRepo;
    private final SessionPollService pollService;
    private final PollingProperties props;

    public SessionPollingScheduler(BlocksSessionRepository sessionRepo,
                                   SessionPollService pollService,
                                   PollingProperties props) {
        this.sessionRepo = sessionRepo;
        this.pollService = pollService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${mesha.polling.interval-ms:5000}", initialDelayString = "10000")
    public void pollActiveSessions() {
        log.debug("polling_cycle_start");

        List<UUID> sessionIds;
        try {
            sessionIds = fetchActiveSessionIds();
        } catch (Exception e) {
            log.error("polling_cycle_query_error error={}", e.getMessage(), e);
            return;
        }

        if (sessionIds.isEmpty()) {
            log.debug("polling_cycle_no_sessions");
            return;
        }

        log.info("polling_cycle_sessions_found count={}", sessionIds.size());

        int processed = 0;
        int errors = 0;
        for (UUID sessionId : sessionIds) {
            try {
                pollService.processSession(sessionId);
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("polling_session_error session_id={} error={}", sessionId, e.getMessage(), e);
            }
        }

        log.info("polling_cycle_end processed={} errors={}", processed, errors);
    }

    private List<UUID> fetchActiveSessionIds() {
        return sessionRepo.findAllByExecutionStateNotIn(SessionPollService.TERMINAL_STATES)
                .stream()
                .map(BlocksSession::getId)
                .toList();
    }
}
