package com.mesha.worker.blocks;

import com.mesha.worker.observability.WorkflowTracer;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult;
import com.mesha.worker.orchestration.SessionResult.SessionStatus;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.mesha.worker.orchestration.SessionResult.SessionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BlocksAdapterTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private BlocksAdapter adapter;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        var workflowTracer = new WorkflowTracer(new SimpleTracer());
        adapter = new BlocksAdapter(workflowTracer, restClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        MDC.clear();
        mocks.close();
    }

    // ---- providerName ----

    @Test
    void providerName_returnsBlocks() {
        assertThat(adapter.providerName()).isEqualTo("blocks");
    }

    // ---- createSession ----

    @Test
    void createSession_returnsPendingResultWithProviderSessionId() {
        stubCreate(new BlocksAdapter.CreateSessionResponse("sess-abc", "pending"));

        var result = adapter.createSession(new SessionRequest("issue-1", "Fix bug", "Details", "main"));

        assertThat(result.providerSessionId()).isEqualTo("sess-abc");
        assertThat(result.status()).isEqualTo(PENDING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void createSession_clearsMdcAfterSuccess() {
        stubCreate(new BlocksAdapter.CreateSessionResponse("sess-xyz", "pending"));

        adapter.createSession(new SessionRequest("i-1", "T", "D", "R"));

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    @Test
    void createSession_throwsWhenResponseIsNull() {
        when(restClient.post().uri(any(String.class)).body(any()).retrieve()
                .body(any(Class.class))).thenReturn(null);

        assertThatThrownBy(() -> adapter.createSession(new SessionRequest("i-1", "T", "D", "R")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty or missing session_id");
    }

    @Test
    void createSession_propagatesRestClientException() {
        when(restClient.post().uri(any(String.class)).body(any()).retrieve()
                .body(any(Class.class))).thenThrow(new RestClientException("network error"));

        assertThatThrownBy(() -> adapter.createSession(new SessionRequest("i-1", "T", "D", "R")))
                .isInstanceOf(RestClientException.class)
                .hasMessage("network error");
    }

    @Test
    void createSession_clearsMdcAfterException() {
        when(restClient.post().uri(any(String.class)).body(any()).retrieve()
                .body(any(Class.class))).thenThrow(new RestClientException("fail"));

        try {
            adapter.createSession(new SessionRequest("i-1", "T", "D", "R"));
        } catch (RestClientException ignored) {}

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    // ---- pollSession ----

    @Test
    void pollSession_returnsResultWithSessionIdAndStatus() {
        stubPoll(new BlocksAdapter.PollSessionResponse("sess-1", "running", null));

        var result = adapter.pollSession("sess-1");

        assertThat(result.providerSessionId()).isEqualTo("sess-1");
        assertThat(result.status()).isEqualTo(RUNNING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void pollSession_extractsFinalMessageOnCompletion() {
        stubPoll(new BlocksAdapter.PollSessionResponse("sess-2", "completed", "PR opened successfully"));

        var result = adapter.pollSession("sess-2");

        assertThat(result.status()).isEqualTo(COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("PR opened successfully");
    }

    @Test
    void pollSession_throwsWhenResponseIsNull() {
        when(restClient.get().uri(any(String.class), any(Object.class)).retrieve()
                .body(any(Class.class))).thenReturn(null);

        assertThatThrownBy(() -> adapter.pollSession("sess-null"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void pollSession_propagatesRestClientException() {
        when(restClient.get().uri(any(String.class), any(Object.class)).retrieve()
                .body(any(Class.class))).thenThrow(new RestClientException("timeout"));

        assertThatThrownBy(() -> adapter.pollSession("sess-err"))
                .isInstanceOf(RestClientException.class)
                .hasMessage("timeout");
    }

    @Test
    void pollSession_clearsMdcAfterSuccess() {
        stubPoll(new BlocksAdapter.PollSessionResponse("sess-3", "running", null));

        adapter.pollSession("sess-3");

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    // ---- mapStatus ----

    @ParameterizedTest
    @CsvSource({
            "pending,   PENDING",
            "running,   RUNNING",
            "in_progress, RUNNING",
            "completed, COMPLETED",
            "done,      COMPLETED",
            "succeeded, COMPLETED",
            "failed,    FAILED",
            "error,     FAILED",
            "cancelled, FAILED",
            "canceled,  FAILED",
    })
    void mapStatus_mapsKnownStatuses(String raw, SessionStatus expected) {
        assertThat(adapter.mapStatus(raw)).isEqualTo(expected);
    }

    @Test
    void mapStatus_treatsUnknownValueAsPending() {
        assertThat(adapter.mapStatus("queued")).isEqualTo(PENDING);
    }

    @Test
    void mapStatus_treatsNullAsPending() {
        assertThat(adapter.mapStatus(null)).isEqualTo(PENDING);
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private void stubCreate(BlocksAdapter.CreateSessionResponse response) {
        when(restClient.post().uri(any(String.class)).body(any()).retrieve()
                .body(any(Class.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void stubPoll(BlocksAdapter.PollSessionResponse response) {
        when(restClient.get().uri(any(String.class), any(Object.class)).retrieve()
                .body(any(Class.class))).thenReturn(response);
    }
}
