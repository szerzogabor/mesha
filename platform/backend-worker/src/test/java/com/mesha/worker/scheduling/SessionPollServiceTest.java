package com.mesha.worker.scheduling;

import com.mesha.worker.blocks.BlocksAdapter;
import com.mesha.worker.orchestration.SessionRequest;
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
import org.springframework.web.client.HttpClientErrorException;
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
    @Mock private IssueWorkerRepository issueRepo;
    @Mock private BlocksMessageWorkerRepository messageRepo;
    @Mock private BlocksAdapter blocksAdapter;
    @Mock private BlocksApiKeyService apiKeyService;
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
        service = new SessionPollService(sessionRepo, issueRepo, messageRepo, blocksAdapter, apiKeyService, redisTemplate, props);
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
    void processSession_dispatchesSessionWhenProviderSessionIdIsNull() {
        UUID sessionId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        BlocksSessionRecord session = sessionWithIssue(sessionId, CREATED, null, 0, Instant.now().minusSeconds(10), issueId);
        IssueWorkerRecord issue = issueRecord(issueId, "Fix login bug", "Description here");
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(issueRepo.findById(issueId)).thenReturn(Optional.of(issue));
        when(apiKeyService.resolveApiKey(issueId)).thenReturn(Optional.of("ws-api-key-xyz"));
        when(blocksAdapter.createSession(any(SessionRequest.class)))
                .thenReturn(new SessionResult("prov-123", SessionStatus.PENDING, null));

        service.processSession(sessionId);

        ArgumentCaptor<SessionRequest> reqCaptor = ArgumentCaptor.forClass(SessionRequest.class);
        verify(blocksAdapter).createSession(reqCaptor.capture());
        assertThat(reqCaptor.getValue().issueId()).isEqualTo(issueId.toString());
        assertThat(reqCaptor.getValue().issueTitle()).isEqualTo("Fix login bug");
        assertThat(reqCaptor.getValue().apiKey()).isEqualTo("ws-api-key-xyz");

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getProviderSessionId()).isEqualTo("prov-123");
    }

    @Test
    void processSession_failsSessionWhenIssueNotFound() {
        UUID sessionId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        BlocksSessionRecord session = sessionWithIssue(sessionId, CREATED, null, 0, Instant.now().minusSeconds(10), issueId);
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(issueRepo.findById(issueId)).thenReturn(Optional.empty());

        service.processSession(sessionId);

        verifyNoInteractions(blocksAdapter);
        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(FAILED);
    }

    @Test
    void processSession_failsSessionWhenApiKeyNotFound() {
        UUID sessionId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        BlocksSessionRecord session = sessionWithIssue(sessionId, CREATED, null, 0, Instant.now().minusSeconds(10), issueId);
        IssueWorkerRecord issue = issueRecord(issueId, "Fix login bug", "Description here");
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(issueRepo.findById(issueId)).thenReturn(Optional.of(issue));
        when(apiKeyService.resolveApiKey(issueId)).thenReturn(Optional.empty());

        service.processSession(sessionId);

        verifyNoInteractions(blocksAdapter);
        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getExecutionState()).isEqualTo(FAILED);
    }

    @Test
    void processSession_incrementsRetryCountOnTransientDispatchFailure() {
        UUID sessionId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        BlocksSessionRecord session = sessionWithIssue(sessionId, CREATED, null, 0, Instant.now().minusSeconds(10), issueId);
        IssueWorkerRecord issue = issueRecord(issueId, "Fix login bug", null);
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(issueRepo.findById(issueId)).thenReturn(Optional.of(issue));
        when(apiKeyService.resolveApiKey(issueId)).thenReturn(Optional.of("ws-api-key-xyz"));
        when(blocksAdapter.createSession(any())).thenThrow(new RestClientException("connection refused"));

        service.processSession(sessionId);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo).save(saved.capture());
        assertThat(saved.getValue().getRetryCount()).isEqualTo(1);
        assertThat(saved.getValue().getExecutionState()).isEqualTo(CREATED);
    }

    @Test
    void processSession_failsSessionOnClientErrorDuringDispatch() {
        UUID sessionId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        BlocksSessionRecord session = sessionWithIssue(sessionId, CREATED, null, 0, Instant.now().minusSeconds(10), issueId);
        IssueWorkerRecord issue = issueRecord(issueId, "Fix login bug", null);
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(issueRepo.findById(issueId)).thenReturn(Optional.of(issue));
        when(apiKeyService.resolveApiKey(issueId)).thenReturn(Optional.of("ws-api-key-xyz"));
        when(blocksAdapter.createSession(any())).thenThrow(
                HttpClientErrorException.create(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Forbidden", org.springframework.http.HttpHeaders.EMPTY,
                        "{\"message\":\"Forbidden\"}".getBytes(), null));

        service.processSession(sessionId);

        ArgumentCaptor<BlocksSessionRecord> saved = ArgumentCaptor.forClass(BlocksSessionRecord.class);
        verify(sessionRepo, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).anyMatch(s -> s.getExecutionState() == FAILED);
    }

    @Test
    void processSession_appliesBackoffOnDispatchRetry() {
        UUID sessionId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        BlocksSessionRecord session = sessionWithIssue(sessionId, CREATED, null, 1, Instant.now().minusSeconds(10), issueId);
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        service.processSession(sessionId);

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

    // ---- helpers ----

    private BlocksSessionRecord sessionWith(UUID id, AIExecutionState state,
                                            String providerSessionId, int retryCount,
                                            Instant createdAt) {
        return sessionWithIssue(id, state, providerSessionId, retryCount, createdAt, UUID.randomUUID());
    }

    private BlocksSessionRecord sessionWithIssue(UUID id, AIExecutionState state,
                                                  String providerSessionId, int retryCount,
                                                  Instant createdAt, UUID issueId) {
        try {
            var ctor = BlocksSessionRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            BlocksSessionRecord s = ctor.newInstance();

            setField(s, BlocksSessionRecord.class, "id", id);
            setField(s, BlocksSessionRecord.class, "issueId", issueId);
            setField(s, BlocksSessionRecord.class, "executionState", state);
            setField(s, BlocksSessionRecord.class, "providerSessionId", providerSessionId);
            setField(s, BlocksSessionRecord.class, "retryCount", retryCount);
            setField(s, BlocksSessionRecord.class, "createdAt", createdAt);
            setField(s, BlocksSessionRecord.class, "updatedAt", createdAt);
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private IssueWorkerRecord issueRecord(UUID id, String title, String description) {
        try {
            var ctor = IssueWorkerRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            IssueWorkerRecord issue = ctor.newInstance();
            setField(issue, IssueWorkerRecord.class, "id", id);
            setField(issue, IssueWorkerRecord.class, "title", title);
            setField(issue, IssueWorkerRecord.class, "description", description);
            return issue;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, Class<?> clazz, String fieldName, Object value) throws Exception {
        var field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        setField(obj, BlocksSessionRecord.class, fieldName, value);
    }
}
