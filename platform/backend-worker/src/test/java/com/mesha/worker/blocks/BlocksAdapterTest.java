package com.mesha.worker.blocks;

import com.mesha.worker.observability.WorkflowTracer;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult.SessionStatus;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.mesha.worker.orchestration.SessionResult.SessionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class BlocksAdapterTest {

    // Explicit mocks for each level of the POST chain
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec postSpec;
    @Mock private RestClient.RequestBodySpec requestBodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;
    // Raw type to avoid wildcard-generic issues with RequestHeadersUriSpec<?>
    @SuppressWarnings("rawtypes")
    @Mock private RestClient.RequestHeadersUriSpec getSpec;

    private BlocksAdapter adapter;
    private AutoCloseable mocks;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        adapter = new BlocksAdapter(new WorkflowTracer(new SimpleTracer()), restClient);

        // POST chain: post() → uri() → body() → retrieve() → body(Class)
        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        // GET chain: get() → uri() → retrieve() → body(Class)
        when(restClient.get()).thenReturn(getSpec);
        // uri(String, Object...) — use doReturn to handle raw-type + varargs safely
        doReturn(getSpec).when(getSpec).uri(anyString(), any(Object[].class));
        when(getSpec.retrieve()).thenReturn(responseSpec);
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
        when(responseSpec.body(BlocksAdapter.CreateSessionResponse.class))
                .thenReturn(new BlocksAdapter.CreateSessionResponse("sess-abc", "pending"));

        var result = adapter.createSession(new SessionRequest("issue-1", "Fix bug", "Details", "main"));

        assertThat(result.providerSessionId()).isEqualTo("sess-abc");
        assertThat(result.status()).isEqualTo(PENDING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void createSession_clearsMdcAfterSuccess() {
        when(responseSpec.body(BlocksAdapter.CreateSessionResponse.class))
                .thenReturn(new BlocksAdapter.CreateSessionResponse("sess-xyz", "pending"));

        adapter.createSession(new SessionRequest("i-1", "T", "D", "R"));

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    @Test
    void createSession_throwsWhenSessionIdIsNull() {
        when(responseSpec.body(BlocksAdapter.CreateSessionResponse.class))
                .thenReturn(new BlocksAdapter.CreateSessionResponse(null, "pending"));

        assertThatThrownBy(() -> adapter.createSession(new SessionRequest("i-1", "T", "D", "R")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty or missing session_id");
    }

    @Test
    void createSession_throwsWhenResponseIsNull() {
        when(responseSpec.body(BlocksAdapter.CreateSessionResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> adapter.createSession(new SessionRequest("i-1", "T", "D", "R")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty or missing session_id");
    }

    @Test
    void createSession_propagatesRestClientException() {
        when(responseSpec.body(BlocksAdapter.CreateSessionResponse.class))
                .thenThrow(new RestClientException("network error"));

        assertThatThrownBy(() -> adapter.createSession(new SessionRequest("i-1", "T", "D", "R")))
                .isInstanceOf(RestClientException.class)
                .hasMessage("network error");
    }

    @Test
    void createSession_clearsMdcAfterException() {
        when(responseSpec.body(BlocksAdapter.CreateSessionResponse.class))
                .thenThrow(new RestClientException("fail"));

        try {
            adapter.createSession(new SessionRequest("i-1", "T", "D", "R"));
        } catch (RestClientException ignored) {}

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    // ---- pollSession ----

    @Test
    void pollSession_returnsResultWithSessionIdAndStatus() {
        when(responseSpec.body(BlocksAdapter.PollSessionResponse.class))
                .thenReturn(new BlocksAdapter.PollSessionResponse("sess-1", "running", null));

        var result = adapter.pollSession("sess-1");

        assertThat(result.providerSessionId()).isEqualTo("sess-1");
        assertThat(result.status()).isEqualTo(RUNNING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void pollSession_extractsFinalMessageOnCompletion() {
        when(responseSpec.body(BlocksAdapter.PollSessionResponse.class))
                .thenReturn(new BlocksAdapter.PollSessionResponse("sess-2", "completed", "PR opened successfully"));

        var result = adapter.pollSession("sess-2");

        assertThat(result.status()).isEqualTo(COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("PR opened successfully");
    }

    @Test
    void pollSession_throwsWhenResponseIsNull() {
        when(responseSpec.body(BlocksAdapter.PollSessionResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> adapter.pollSession("sess-null"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void pollSession_propagatesRestClientException() {
        when(responseSpec.body(BlocksAdapter.PollSessionResponse.class))
                .thenThrow(new RestClientException("timeout"));

        assertThatThrownBy(() -> adapter.pollSession("sess-err"))
                .isInstanceOf(RestClientException.class)
                .hasMessage("timeout");
    }

    @Test
    void pollSession_clearsMdcAfterSuccess() {
        when(responseSpec.body(BlocksAdapter.PollSessionResponse.class))
                .thenReturn(new BlocksAdapter.PollSessionResponse("sess-3", "running", null));

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
}
