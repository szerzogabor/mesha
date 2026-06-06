package com.mesha.worker.scheduling;

import com.mesha.worker.blocks.BlocksAdapter;
import com.mesha.worker.orchestration.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Transactional helper invoked once per active session during each polling cycle.
 * Keeping per-session work in its own transaction means a failure on one session
 * never rolls back updates already applied to others.
 */
@Service
class SessionPollService {

    private static final Logger log = LoggerFactory.getLogger(SessionPollService.class);

    static final Set<AIExecutionState> TERMINAL_STATES = EnumSet.of(
            AIExecutionState.DONE, AIExecutionState.FAILED, AIExecutionState.CANCELED);

    private final BlocksSessionPollerRepository sessionRepo;
    private final BlocksAdapter blocksAdapter;
    private final RedisTemplate<String, String> redisTemplate;
    private final PollingProperties props;

    SessionPollService(BlocksSessionPollerRepository sessionRepo,
                       BlocksAdapter blocksAdapter,
                       RedisTemplate<String, String> redisTemplate,
                       PollingProperties props) {
        this.sessionRepo = sessionRepo;
        this.blocksAdapter = blocksAdapter;
        this.redisTemplate = redisTemplate;
        this.props = props;
    }

    /**
     * Processes a single session: applies the max-age guard first, then attempts
     * an exponential-backoff-gated poll against the Blocks API.
     *
     * @param sessionId ID of the session to process
     */
    @Transactional
    void processSession(UUID sessionId) {
        BlocksSessionRecord session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null || TERMINAL_STATES.contains(session.getExecutionState())) {
            return;
        }

        if (isExpired(session)) {
            failSession(session, "Session exceeded maximum age of " + props.maxSessionAgeHours() + " hours");
            return;
        }

        if (session.getProviderSessionId() == null) {
            log.debug("session_no_provider_id session_id={} state={}",
                    sessionId, session.getExecutionState());
            return;
        }

        if (!acquireBackoffLock(session)) {
            return;
        }

        poll(session);
    }

    private boolean isExpired(BlocksSessionRecord session) {
        Duration age = Duration.between(session.getCreatedAt(), Instant.now());
        return age.toHours() >= props.maxSessionAgeHours();
    }

    /**
     * Uses Redis setIfAbsent as a combined distributed lock and backoff gate.
     * The TTL equals the computed backoff interval, so the session cannot be
     * polled again until that interval elapses — even across multiple instances.
     */
    private boolean acquireBackoffLock(BlocksSessionRecord session) {
        Duration backoff = computeBackoff(session.getRetryCount());
        String key = "mesha:session:poll:" + session.getId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", backoff);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("session_backoff_active session_id={} backoff_ms={}",
                    session.getId(), backoff.toMillis());
            return false;
        }
        return true;
    }

    private void poll(BlocksSessionRecord session) {
        try {
            SessionResult result = blocksAdapter.pollSession(session.getProviderSessionId());
            AIExecutionState newState = mapToExecutionState(result.status(), session.getExecutionState());

            String oldState = session.getExecutionState().name();
            session.setRetryCount(session.getRetryCount() + 1);

            if (newState != session.getExecutionState()) {
                session.setExecutionState(newState);
                log.info("session_state_changed session_id={} from={} to={} provider_session_id={}",
                        session.getId(), oldState, newState, session.getProviderSessionId());
            } else {
                log.debug("session_state_unchanged session_id={} state={} poll_count={}",
                        session.getId(), newState, session.getRetryCount());
            }

            sessionRepo.save(session);

        } catch (Exception e) {
            log.error("session_poll_failure session_id={} provider_session_id={} error={}",
                    session.getId(), session.getProviderSessionId(), e.getMessage(), e);
            // Let the backoff lock expire; session will be retried on next eligible cycle.
        }
    }

    private void failSession(BlocksSessionRecord session, String reason) {
        Duration age = Duration.between(session.getCreatedAt(), Instant.now());
        log.warn("session_expired session_id={} age_hours={} reason={}",
                session.getId(), age.toHours(), reason);
        session.setExecutionState(AIExecutionState.FAILED);
        session.setErrorMessage(reason);
        sessionRepo.save(session);
    }

    /**
     * Computes the backoff interval for the next poll.
     * Formula: min(baseMs × multiplier^pollCount, maxMs)
     */
    Duration computeBackoff(int pollCount) {
        double backoffMs = props.backoff().baseMs();
        for (int i = 0; i < pollCount; i++) {
            backoffMs *= props.backoff().multiplier();
            if (backoffMs >= props.backoff().maxMs()) {
                return Duration.ofMillis(props.backoff().maxMs());
            }
        }
        return Duration.ofMillis((long) Math.min(backoffMs, props.backoff().maxMs()));
    }

    /**
     * Maps a Blocks API status to the canonical AIExecutionState.
     * PENDING is treated as PLANNING on first poll (CREATED → PLANNING),
     * or left unchanged if the session is already further along.
     */
    AIExecutionState mapToExecutionState(SessionResult.SessionStatus status, AIExecutionState current) {
        return switch (status) {
            case PENDING -> current == AIExecutionState.CREATED ? AIExecutionState.PLANNING : current;
            case RUNNING -> AIExecutionState.EXECUTING;
            case COMPLETED -> AIExecutionState.DONE;
            case FAILED -> AIExecutionState.FAILED;
        };
    }
}
