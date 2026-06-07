package com.mesha.worker.scheduling;

import com.mesha.worker.blocks.BlocksAdapter;
import com.mesha.worker.orchestration.SessionResult;
import com.mesha.worker.orchestration.SessionResult.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.mesha.worker.scheduling.AIExecutionState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionPollServiceTest {

    @Mock private BlocksSessionPollerRepository sessionRepo;
    @Mock private BlocksMessageWorkerRepository messageRepo;
    @Mock private BlocksAdapter blocksAdapter;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private PollingProperties props;
    private SessionPollService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        props = new PollingProperties(5000L, 24L,
                new PollingProperties.BackoffProperties(5000L, 300_000L, 2.0));
        service = new SessionPollService(sessionRepo, messageRepo, blocksAdapter, redisTemplate, props);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ---- processSession skips ----

    @Test
    void processSession_skipsWhenSessionNotFound() {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.empty());

        service.processSession(id);

        verifyNoInteractions(blocksAdapter);
    }

    @ParameterizedTest
    @CsvSource({"DONE", "FAILED", "CANCELED"})
    void processSession_skipsTerminalSession(AIExecutionState state) {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findById(id)).thenReturn(Optional.of(
                sessionWith(id, state, "sid-1", 0, Instant.now().minusSeconds(60))));

        service.processSession(id);

        verifyNoInteractions(blocksAdapter);
    }

    @Test
    void processSession_skipsWhenProviderSessionIdIsNull() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, CREATED, null, 0, Instant.now().minusSeconds(10));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));

        service.processSession(id);

        verifyNoInteractions(blocksAdapter);
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void processSession_skipsWhenBackoffLockNotAcquired() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 3, Instant.now().minusSeconds(30));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        service.processSession(id);

        verifyNoInteractions(blocksAdapter);
        verify(sessionRepo, never()).save(any());
    }

    // ---- max-age guard ----

    @Test
    void processSession_failsExpiredSession() {
        UUID id = UUID.randomUUID();
        Instant tooOld = Instant.now().minus(Duration.ofHours(25));
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 5, tooOld);
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));

        service.processSession(id);

        verifyNoInteractions(blocksAdapter);
        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(FAILED);
        assertThat(saved.getValue().getErrorMessage()).contains("maximum age");
    }

    @Test
    void processSession_doesNotFailSessionWithinMaxAge() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 0, Instant.now().minusSeconds(60));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1"))
                .thenReturn(new SessionResult("sid-1", SessionStatus.RUNNING, null));

        service.processSession(id);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(EXECUTING);
    }

    // ---- state mapping ----

    @Test
    void processSession_mapsPendingToPlanning_whenCurrentStateIsCreated() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, CREATED, "sid-1", 0, Instant.now().minusSeconds(5));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1"))
                .thenReturn(new SessionResult("sid-1", SessionStatus.PENDING, null));

        service.processSession(id);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(PLANNING);
    }

    @Test
    void processSession_keepsPlanningWhenPendingAndAlreadyPlanning() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, PLANNING, "sid-1", 1, Instant.now().minusSeconds(10));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1"))
                .thenReturn(new SessionResult("sid-1", SessionStatus.PENDING, null));

        service.processSession(id);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(PLANNING);
    }

    @Test
    void processSession_mapsRunningToExecuting() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, PLANNING, "sid-1", 1, Instant.now().minusSeconds(15));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1"))
                .thenReturn(new SessionResult("sid-1", SessionStatus.RUNNING, null));

        service.processSession(id);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(EXECUTING);
    }

    @Test
    void processSession_mapsCompletedToDone() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 5, Instant.now().minusSeconds(120));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1"))
                .thenReturn(new SessionResult("sid-1", SessionStatus.COMPLETED, "Done!"));

        service.processSession(id);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(DONE);
    }

    @Test
    void processSession_mapsFailedToFailed() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 4, Instant.now().minusSeconds(90));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1"))
                .thenReturn(new SessionResult("sid-1", SessionStatus.FAILED, null));

        service.processSession(id);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(FAILED);
    }

    // ---- poll error resilience ----

    @Test
    void processSession_doesNotUpdateStateOnPollFailure() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 2, Instant.now().minusSeconds(30));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1")).thenThrow(new RestClientException("timeout"));

        service.processSession(id);

        verify(sessionRepo, never()).save(any());
    }

    // ---- retryCount increments ----

    @Test
    void processSession_incrementsRetryCountOnEachPoll() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 3, Instant.now().minusSeconds(30));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(blocksAdapter.pollSession("sid-1"))
                .thenReturn(new SessionResult("sid-1", SessionStatus.RUNNING, null));

        service.processSession(id);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getRetryCount()).isEqualTo(4);
    }

    // ---- computeBackoff ----

    @Test
    void computeBackoff_returnBaseOnFirstPoll() {
        assertThat(service.computeBackoff(0)).isEqualTo(Duration.ofMillis(5000));
    }

    @Test
    void computeBackoff_doublesEachPoll() {
        assertThat(service.computeBackoff(1)).isEqualTo(Duration.ofMillis(10_000));
        assertThat(service.computeBackoff(2)).isEqualTo(Duration.ofMillis(20_000));
        assertThat(service.computeBackoff(3)).isEqualTo(Duration.ofMillis(40_000));
    }

    @Test
    void computeBackoff_capsAtMaxMs() {
        assertThat(service.computeBackoff(100)).isEqualTo(Duration.ofMillis(300_000));
    }

    // ---- mapToExecutionState ----

    @ParameterizedTest
    @CsvSource({
            "PENDING,  CREATED,   PLANNING",
            "PENDING,  PLANNING,  PLANNING",
            "PENDING,  EXECUTING, EXECUTING",
            "RUNNING,  CREATED,   EXECUTING",
            "RUNNING,  PLANNING,  EXECUTING",
            "COMPLETED,EXECUTING, DONE",
            "FAILED,   EXECUTING, FAILED",
    })
    void mapToExecutionState_correctTransitions(
            SessionStatus status,
            AIExecutionState current,
            AIExecutionState expected) {
        assertThat(service.mapToExecutionState(status, current)).isEqualTo(expected);
    }

    // ---- Redis lock key format ----

    @Test
    void processSession_usesSessionIdInRedisKey() {
        UUID id = UUID.randomUUID();
        BlocksSessionRecord session = sessionWith(id, EXECUTING, "sid-1", 0, Instant.now().minusSeconds(5));
        when(sessionRepo.findById(id)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        service.processSession(id);

        verify(valueOps).setIfAbsent(eq("mesha:session:poll:" + id), anyString(), any(Duration.class));
    }

    // ---- helper ----

    private BlocksSessionRecord sessionWith(UUID id, AIExecutionState state,
                                            String providerSessionId, int retryCount,
                                            Instant createdAt) {
        try {
            var ctor = BlocksSessionRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            BlocksSessionRecord s = ctor.newInstance();

            setField(s, "id", id);
            setField(s, "executionState", state);
            setField(s, "providerSessionId", providerSessionId);
            setField(s, "retryCount", retryCount);
            setField(s, "createdAt", createdAt);
            setField(s, "updatedAt", createdAt);
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = BlocksSessionRecord.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
