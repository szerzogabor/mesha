package com.mesha.api.worker.scheduling;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.worker.blocks.BlocksAdapter;
import com.mesha.api.worker.orchestration.SessionResult;
import com.mesha.api.worker.scheduling.SessionPollTransactions.SessionSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies the DISPATCHING dispatch-claim mechanism prevents concurrent workers from
 * creating multiple Blocks provider sessions for the same DB record.
 */
class SessionDispatchClaimTest {

    @Mock private SessionPollTransactions txns;
    @Mock private BlocksAdapter blocksAdapter;
    @Mock private BlocksApiKeyService apiKeyService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private PollingProperties props;
    @Mock private PollingProperties.BackoffProperties backoffConfig;

    private SessionPollService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(props.backoff()).thenReturn(backoffConfig);
        when(backoffConfig.baseMs()).thenReturn(5000L);
        when(backoffConfig.multiplier()).thenReturn(2.0);
        when(backoffConfig.maxMs()).thenReturn(300_000L);
        when(props.maxSessionAgeHours()).thenReturn(24L);

        service = new SessionPollService(txns, blocksAdapter, apiKeyService, redisTemplate, props,
                "https://www.blocks.team");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // -------------------------------------------------------------------------
    // claimForDispatch semantics
    // -------------------------------------------------------------------------

    @Test
    void processSession_firstWorker_claimsAndDispatches() {
        UUID sessionId = UUID.randomUUID();
        SessionSnapshot snapshot = createdSnapshot(sessionId);

        when(txns.loadSnapshot(sessionId)).thenReturn(snapshot);
        when(txns.claimForDispatch(sessionId)).thenReturn(true);
        when(txns.loadDispatchInputs(sessionId)).thenReturn(null); // triggers markFailed path

        service.processSession(sessionId);

        verify(txns).claimForDispatch(sessionId);
        // loadDispatchInputs was called, proving dispatch was attempted
        verify(txns).loadDispatchInputs(sessionId);
    }

    @Test
    void processSession_secondWorker_claimLost_doesNotDispatch() {
        UUID sessionId = UUID.randomUUID();
        SessionSnapshot snapshot = createdSnapshot(sessionId);

        when(txns.loadSnapshot(sessionId)).thenReturn(snapshot);
        when(txns.claimForDispatch(sessionId)).thenReturn(false); // lost the race

        service.processSession(sessionId);

        verify(txns).claimForDispatch(sessionId);
        verify(txns, never()).loadDispatchInputs(any());
        verify(blocksAdapter, never()).createSession(any());
    }

    @Test
    void processSession_terminalState_skipsDispatch() {
        UUID sessionId = UUID.randomUUID();
        SessionSnapshot snapshot = snapshotWithState(sessionId, AIExecutionState.DONE);

        when(txns.loadSnapshot(sessionId)).thenReturn(snapshot);

        service.processSession(sessionId);

        verify(txns, never()).claimForDispatch(any());
        verify(blocksAdapter, never()).createSession(any());
    }

    // -------------------------------------------------------------------------
    // DISPATCHING recovery (pod-crash resilience)
    // -------------------------------------------------------------------------

    @Test
    void processSession_staleDispatching_reverts() {
        UUID sessionId = UUID.randomUUID();
        // updatedAt 10 minutes ago → stale
        SessionSnapshot snapshot = dispatchingSnapshotWithAge(sessionId, Duration.ofMinutes(10));

        when(txns.loadSnapshot(sessionId)).thenReturn(snapshot);

        service.processSession(sessionId);

        verify(txns).revertStaleDispatch(sessionId);
        verify(txns, never()).claimForDispatch(any());
        verify(blocksAdapter, never()).createSession(any());
    }

    @Test
    void processSession_freshDispatching_waits() {
        UUID sessionId = UUID.randomUUID();
        // updatedAt 1 minute ago → not yet stale; another pod is likely still working
        SessionSnapshot snapshot = dispatchingSnapshotWithAge(sessionId, Duration.ofMinutes(1));

        when(txns.loadSnapshot(sessionId)).thenReturn(snapshot);

        service.processSession(sessionId);

        verify(txns, never()).revertStaleDispatch(any());
        verify(txns, never()).claimForDispatch(any());
        verify(blocksAdapter, never()).createSession(any());
    }

    // -------------------------------------------------------------------------
    // Concurrent workers — only one dispatch per session
    // -------------------------------------------------------------------------

    @Test
    void concurrentWorkers_onlyOneClaimsDispatch() throws InterruptedException {
        UUID sessionId = UUID.randomUUID();
        SessionSnapshot snapshot = createdSnapshot(sessionId);

        AtomicInteger claimCount = new AtomicInteger(0);
        AtomicInteger dispatchCount = new AtomicInteger(0);

        // Simulate atomic DB behaviour: only the first caller gets true
        when(txns.loadSnapshot(sessionId)).thenReturn(snapshot);
        when(txns.claimForDispatch(sessionId)).thenAnswer(inv ->
                claimCount.getAndIncrement() == 0);
        when(txns.loadDispatchInputs(sessionId)).thenAnswer(inv -> {
            dispatchCount.incrementAndGet();
            return null; // triggers markFailed quickly — we only need the count
        });

        int workerCount = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workerCount);
        ExecutorService pool = Executors.newFixedThreadPool(workerCount);

        for (int i = 0; i < workerCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.processSession(sessionId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown(); // release all workers simultaneously
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(claimCount.get()).isEqualTo(workerCount);
        // Only one worker proceeded past the claim gate
        assertThat(dispatchCount.get()).isEqualTo(1);
        // blocksAdapter.createSession is never reached because loadDispatchInputs returned null,
        // but the key assertion is that dispatch was attempted exactly once.
        verify(blocksAdapter, never()).createSession(any());
    }

    // -------------------------------------------------------------------------
    // State transitions on success / failure
    // -------------------------------------------------------------------------

    @Test
    void mapToExecutionState_pending_fromCreated_returnsPlanning() {
        AIExecutionState result = service.mapToExecutionState(
                SessionResult.SessionStatus.PENDING, AIExecutionState.CREATED);
        assertThat(result).isEqualTo(AIExecutionState.PLANNING);
    }

    @Test
    void mapToExecutionState_pending_fromDispatching_returnsPlanning() {
        // DISPATCHING is treated identically to CREATED for the PENDING transition
        AIExecutionState result = service.mapToExecutionState(
                SessionResult.SessionStatus.PENDING, AIExecutionState.DISPATCHING);
        assertThat(result).isEqualTo(AIExecutionState.PLANNING);
    }

    @Test
    void mapToExecutionState_pending_fromPlanning_keepsPlanning() {
        AIExecutionState result = service.mapToExecutionState(
                SessionResult.SessionStatus.PENDING, AIExecutionState.PLANNING);
        assertThat(result).isEqualTo(AIExecutionState.PLANNING);
    }

    @Test
    void mapToExecutionState_running_returnsExecuting() {
        AIExecutionState result = service.mapToExecutionState(
                SessionResult.SessionStatus.RUNNING, AIExecutionState.PLANNING);
        assertThat(result).isEqualTo(AIExecutionState.EXECUTING);
    }

    @Test
    void mapToExecutionState_completed_returnsDone() {
        AIExecutionState result = service.mapToExecutionState(
                SessionResult.SessionStatus.COMPLETED, AIExecutionState.EXECUTING);
        assertThat(result).isEqualTo(AIExecutionState.DONE);
    }

    @Test
    void mapToExecutionState_failed_returnsFailed() {
        AIExecutionState result = service.mapToExecutionState(
                SessionResult.SessionStatus.FAILED, AIExecutionState.EXECUTING);
        assertThat(result).isEqualTo(AIExecutionState.FAILED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SessionSnapshot createdSnapshot(UUID sessionId) {
        return new SessionSnapshot(sessionId, UUID.randomUUID(), null, 0,
                AIExecutionState.CREATED, Instant.now().minus(Duration.ofMinutes(1)),
                null, 0, Instant.now().minus(Duration.ofSeconds(10)));
    }

    private SessionSnapshot snapshotWithState(UUID sessionId, AIExecutionState state) {
        return new SessionSnapshot(sessionId, UUID.randomUUID(), null, 0,
                state, Instant.now().minus(Duration.ofMinutes(1)),
                null, 0, Instant.now().minus(Duration.ofSeconds(10)));
    }

    private SessionSnapshot dispatchingSnapshotWithAge(UUID sessionId, Duration stuckFor) {
        return new SessionSnapshot(sessionId, UUID.randomUUID(), null, 0,
                AIExecutionState.DISPATCHING, Instant.now().minus(stuckFor),
                null, 0, Instant.now().minus(stuckFor));
    }
}
