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

import java.util.List;

import static com.mesha.worker.orchestration.SessionResult.SessionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BlocksAdapterTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec postSpec;
    @Mock private RestClient.RequestBodySpec requestBodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;
    @SuppressWarnings("rawtypes")
    @Mock private RestClient.RequestHeadersUriSpec getSpec;

    private BlocksAdapter adapter;
    private AutoCloseable mocks;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        adapter = new BlocksAdapter(new WorkflowTracer(new SimpleTracer()), restClient);

        // POST chain — doReturn avoids invoking the generic <T> body(T) method during
        // stub registration, which can lose the any() matcher due to type-erasure quirks.
        doReturn(postSpec).when(restClient).post();
        doReturn(requestBodySpec).when(postSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).headers(any());
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        doReturn(responseSpec).when(requestBodySpec).retrieve();

        // GET chain
        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), (Object[]) any());
        doReturn(responseSpec).when(getSpec).retrieve();
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
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-abc", "pending"))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        var result = adapter.createSession(minimalRequest("issue-1", "Fix bug", "Details"));

        assertThat(result.providerSessionId()).isEqualTo("sess-abc");
        assertThat(result.status()).isEqualTo(PENDING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void createSession_clearsMdcAfterSuccess() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-xyz", "pending"))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        adapter.createSession(minimalRequest("i-1", "T", "D"));

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    @Test
    void createSession_throwsWhenSessionIdIsNull() {
        doReturn(new BlocksAdapter.CreateSessionResponse(null, "pending"))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        assertThatThrownBy(() -> adapter.createSession(minimalRequest("i-1", "T", "D")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty or missing session_id");
    }

    @Test
    void createSession_throwsWhenResponseIsNull() {
        doReturn(null).when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        assertThatThrownBy(() -> adapter.createSession(minimalRequest("i-1", "T", "D")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty or missing session_id");
    }

    @Test
    void createSession_propagatesRestClientException() {
        doThrow(new RestClientException("network error"))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        assertThatThrownBy(() -> adapter.createSession(minimalRequest("i-1", "T", "D")))
                .isInstanceOf(RestClientException.class)
                .hasMessage("network error");
    }

    @Test
    void createSession_clearsMdcAfterException() {
        doThrow(new RestClientException("fail"))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        try {
            adapter.createSession(minimalRequest("i-1", "T", "D"));
        } catch (RestClientException ignored) {}

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    // ---- pollSession ----

    @Test
    void pollSession_returnsResultWithSessionIdAndStatus() {
        doReturn(new BlocksAdapter.PollSessionResponse("sess-1", "running", null))
                .when(responseSpec).body(BlocksAdapter.PollSessionResponse.class);

        var result = adapter.pollSession("sess-1");

        assertThat(result.providerSessionId()).isEqualTo("sess-1");
        assertThat(result.status()).isEqualTo(RUNNING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void pollSession_extractsFinalMessageOnCompletion() {
        doReturn(new BlocksAdapter.PollSessionResponse("sess-2", "completed", "PR opened successfully"))
                .when(responseSpec).body(BlocksAdapter.PollSessionResponse.class);

        var result = adapter.pollSession("sess-2");

        assertThat(result.status()).isEqualTo(COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("PR opened successfully");
    }

    @Test
    void pollSession_throwsWhenResponseIsNull() {
        doReturn(null).when(responseSpec).body(BlocksAdapter.PollSessionResponse.class);

        assertThatThrownBy(() -> adapter.pollSession("sess-null"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void pollSession_propagatesRestClientException() {
        doThrow(new RestClientException("timeout"))
                .when(responseSpec).body(BlocksAdapter.PollSessionResponse.class);

        assertThatThrownBy(() -> adapter.pollSession("sess-err"))
                .isInstanceOf(RestClientException.class)
                .hasMessage("timeout");
    }

    @Test
    void pollSession_clearsMdcAfterSuccess() {
        doReturn(new BlocksAdapter.PollSessionResponse("sess-3", "running", null))
                .when(responseSpec).body(BlocksAdapter.PollSessionResponse.class);

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

    private SessionRequest minimalRequest(String issueId, String title, String description) {
        return new SessionRequest(
                issueId, title, description,
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key"
        );
    }
}
