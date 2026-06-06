package com.mesha.worker.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.mesha.worker.scheduling.AIExecutionState.*;
import static org.mockito.Mockito.*;

class SessionPollingSchedulerTest {

    @Mock private BlocksSessionPollerRepository sessionRepo;
    @Mock private SessionPollService pollService;

    private PollingProperties props;
    private SessionPollingScheduler scheduler;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        props = new PollingProperties(5000L, 24L,
                new PollingProperties.BackoffProperties(5000L, 300_000L, 2.0));
        scheduler = new SessionPollingScheduler(sessionRepo, pollService, props);
    }

    @Test
    void pollActiveSessions_doesNothingWhenNoActiveSessions() {
        when(sessionRepo.findAllByExecutionStateNotIn(anyCollection())).thenReturn(List.of());

        scheduler.pollActiveSessions();

        verifyNoInteractions(pollService);
    }

    @Test
    void pollActiveSessions_invokesProcessForEachSession() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(sessionRepo.findAllByExecutionStateNotIn(anyCollection()))
                .thenReturn(List.of(
                        sessionRecord(id1, PLANNING),
                        sessionRecord(id2, EXECUTING)
                ));

        scheduler.pollActiveSessions();

        verify(pollService).processSession(id1);
        verify(pollService).processSession(id2);
    }

    @Test
    void pollActiveSessions_continuesAfterSingleSessionError() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(sessionRepo.findAllByExecutionStateNotIn(anyCollection()))
                .thenReturn(List.of(
                        sessionRecord(id1, EXECUTING),
                        sessionRecord(id2, EXECUTING)
                ));
        doThrow(new RuntimeException("transient error")).when(pollService).processSession(id1);

        scheduler.pollActiveSessions();

        verify(pollService).processSession(id1);
        verify(pollService).processSession(id2);
    }

    @Test
    void pollActiveSessions_handlesRepositoryQueryError() {
        when(sessionRepo.findAllByExecutionStateNotIn(anyCollection()))
                .thenThrow(new RuntimeException("DB connection lost"));

        scheduler.pollActiveSessions();

        verifyNoInteractions(pollService);
    }

    @Test
    void pollActiveSessions_queriesOnlyNonTerminalStates() {
        when(sessionRepo.findAllByExecutionStateNotIn(anyCollection())).thenReturn(List.of());

        scheduler.pollActiveSessions();

        verify(sessionRepo).findAllByExecutionStateNotIn(argThat(states ->
                states.contains(DONE) && states.contains(FAILED) && states.contains(CANCELED)
        ));
    }

    // ---- helper ----

    private BlocksSessionRecord sessionRecord(UUID id, AIExecutionState state) {
        try {
            var ctor = BlocksSessionRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            BlocksSessionRecord s = ctor.newInstance();
            setField(s, "id", id);
            setField(s, "executionState", state);
            setField(s, "providerSessionId", "sid-" + id);
            setField(s, "retryCount", 0);
            setField(s, "createdAt", Instant.now().minusSeconds(60));
            setField(s, "updatedAt", Instant.now().minusSeconds(60));
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
