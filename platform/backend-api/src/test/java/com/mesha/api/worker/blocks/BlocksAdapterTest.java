package com.mesha.api.worker.blocks;

import com.mesha.api.worker.observability.WorkflowTracer;
import com.mesha.api.worker.orchestration.SessionRequest;
import com.mesha.api.worker.orchestration.SessionResult.SessionStatus;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static com.mesha.api.worker.orchestration.SessionResult.SessionStatus.*;
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
    @Captor private ArgumentCaptor<Object> bodyCaptor;

    private BlocksAdapter adapter;
    private AutoCloseable mocks;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        adapter = new BlocksAdapter(new WorkflowTracer(new SimpleTracer()), restClient);

        doReturn(postSpec).when(restClient).post();
        doReturn(requestBodySpec).when(postSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).headers(any());
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        doReturn(responseSpec).when(requestBodySpec).retrieve();

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
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-abc", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        var result = adapter.createSession(minimalRequest("issue-1", null, "Fix bug", "Details"));

        assertThat(result.providerSessionId()).isEqualTo("sess-abc");
        assertThat(result.status()).isEqualTo(PENDING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void createSession_clearsMdcAfterSuccess() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-xyz", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        adapter.createSession(minimalRequest("i-1", null, "T", "D"));

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    @Test
    void createSession_throwsWhenSessionIdIsNull() {
        doReturn(new BlocksAdapter.CreateSessionResponse(null, "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        assertThatThrownBy(() -> adapter.createSession(minimalRequest("i-1", null, "T", "D")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty or missing session_id");
    }

    @Test
    void createSession_throwsWhenResponseIsNull() {
        doReturn(null).when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        assertThatThrownBy(() -> adapter.createSession(minimalRequest("i-1", null, "T", "D")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty or missing session_id");
    }

    @Test
    void createSession_propagatesRestClientException() {
        doThrow(new RestClientException("network error"))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        assertThatThrownBy(() -> adapter.createSession(minimalRequest("i-1", null, "T", "D")))
                .isInstanceOf(RestClientException.class)
                .hasMessage("network error");
    }

    @Test
    void createSession_clearsMdcAfterException() {
        doThrow(new RestClientException("fail"))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);

        try {
            adapter.createSession(minimalRequest("i-1", null, "T", "D"));
        } catch (RestClientException ignored) {}

        assertThat(MDC.get("sessionId")).isNull();
        assertThat(MDC.get("provider")).isNull();
    }

    // ---- agent_name resolution ----

    @Test
    void createSession_usesDefaultAgentNameWhenBlocksAgentNameAndAgentLlmAreNull() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        adapter.createSession(minimalRequest("uuid-1", null, "Title", "Desc"));

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.agentName()).isEqualTo("claude");
    }

    @Test
    void createSession_usesBlocksAgentNameWhenSet() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        var request = new SessionRequest(
                "uuid-1", null, "Title", "Desc",
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null, null, "codex", null,
                List.of(), null
        );
        adapter.createSession(request);

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.agentName()).isEqualTo("codex");
    }

    @Test
    void createSession_doesNotUseAgentLlmAsAgentName() {
        // Regression test for TP-83: agentLlm (e.g. "claude-haiku") is an LLM model
        // identifier — it must NOT be passed as agent_name to the Blocks API.
        // Valid agent_name values are: claude, codex, gemini, opencode, cursor, kimi.
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        var request = new SessionRequest(
                "uuid-1", null, "Title", "Desc",
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null, "claude-haiku", null, null,
                List.of(), null
        );
        adapter.createSession(request);

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.agentName()).isNotEqualTo("claude-haiku");
        assertThat(captured.agentName()).isEqualTo("claude");
    }

    @Test
    void createSession_fallsBackToDefaultWhenBlocksAgentNameIsInvalid() {
        // Regression: if blocksAgentName contains an invalid value (e.g. stored before
        // validation was added), it must NOT be forwarded to the Blocks API.
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        var request = new SessionRequest(
                "uuid-1", null, "Title", "Desc",
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null, null, "claude-opus", null,
                List.of(), null
        );
        adapter.createSession(request);

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.agentName()).isEqualTo("claude");
        assertThat(BlocksAdapter.VALID_AGENT_NAMES).doesNotContain("claude-opus");
    }

    @Test
    void validAgentNames_containsAllKnownBlocksAgents() {
        assertThat(BlocksAdapter.VALID_AGENT_NAMES)
                .containsExactlyInAnyOrder("claude", "codex", "gemini", "opencode", "cursor", "kimi");
    }

    // ---- buildMessage / issueIdentifier ----

    @Test
    void buildMessage_includesIssueIdentifierInIssueSection() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        adapter.createSession(minimalRequest("uuid-1", "MES-42", "My Issue", "Description"));

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.message()).contains("Identifier: MES-42");
    }

    @Test
    void buildMessage_includesPrTitleConventionWhenIdentifierPresent() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        adapter.createSession(minimalRequest("uuid-1", "MES-42", "My Issue", "Description"));

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.message()).contains("PR Title Convention");
        assertThat(captured.message()).contains("MES-42: <description>");
        assertThat(captured.message()).contains("MES-42: Add feature X");
    }

    @Test
    void buildMessage_omitsPrTitleConventionWhenNoIdentifier() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        adapter.createSession(minimalRequest("uuid-1", null, "My Issue", "Description"));

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.message()).doesNotContain("PR Title Convention");
        assertThat(captured.message()).doesNotContain("Identifier:");
    }

    @Test
    void buildMessage_omitsPrTitleConventionWhenIdentifierIsBlank() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        adapter.createSession(minimalRequest("uuid-1", "", "My Issue", "Description"));

        BlocksAdapter.CreateSessionRequest captured = (BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue();
        assertThat(captured.message()).doesNotContain("PR Title Convention");
    }

    // ---- buildMessage / startupCommands ----

    @Test
    void buildMessage_singleStartupCommandIsFirstLineOfMessage() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        var request = new SessionRequest(
                "uuid-1", null, "My Issue", "Description",
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null, null, null, null,
                List.of("/claude-haiku"),
                null
        );
        adapter.createSession(request);

        String message = ((BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue()).message();
        assertThat(message).startsWith("/claude-haiku\n");
    }

    @Test
    void buildMessage_multipleStartupCommandsEachOnOwnLine() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        var request = new SessionRequest(
                "uuid-1", null, "My Issue", "Description",
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null, null, null, null,
                List.of("/claude-haiku", "/ultrathink"),
                null
        );
        adapter.createSession(request);

        String message = ((BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue()).message();
        assertThat(message).startsWith("/claude-haiku\n/ultrathink\n\n");
    }

    @Test
    void buildMessage_startupCommandsSeparatedFromBodyByBlankLine() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        var request = new SessionRequest(
                "uuid-1", null, "My Issue", "Description",
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null, null, null, "System prompt here",
                List.of("/claude-haiku"),
                null
        );
        adapter.createSession(request);

        String message = ((BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue()).message();
        assertThat(message).startsWith("/claude-haiku\n\nSystem prompt here");
    }

    // ---- buildMessage / attachments ----

    @Test
    void buildMessage_includesAttachedFilesSection() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        var request = new SessionRequest(
                "uuid-1", "MES-10", "My Issue", "Description",
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null, null, null, null,
                List.of(),
                List.of("design.png (image/png, 1.2 MB)", "spec.pdf (application/pdf, 320 KB)")
        );
        adapter.createSession(request);

        String message = ((BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue()).message();
        assertThat(message).contains("Attached Files");
        assertThat(message).contains("1. design.png (image/png, 1.2 MB)");
        assertThat(message).contains("2. spec.pdf (application/pdf, 320 KB)");
    }

    @Test
    void buildMessage_omitsAttachedFilesSectionWhenEmpty() {
        doReturn(new BlocksAdapter.CreateSessionResponse("sess-id", "pending", null))
                .when(responseSpec).body(BlocksAdapter.CreateSessionResponse.class);
        doReturn(requestBodySpec).when(requestBodySpec).body(bodyCaptor.capture());

        adapter.createSession(minimalRequest("uuid-1", null, "My Issue", "Description"));

        String message = ((BlocksAdapter.CreateSessionRequest) bodyCaptor.getValue()).message();
        assertThat(message).doesNotContain("Attached Files");
    }

    // ---- pollSession ----

    @Test
    void pollSession_returnsResultWithSessionIdAndStatus() {
        doReturn(new BlocksAdapter.PollSessionResponse("sess-1", "running", null, null, null))
                .when(responseSpec).body(BlocksAdapter.PollSessionResponse.class);

        var result = adapter.pollSession("sess-1");

        assertThat(result.providerSessionId()).isEqualTo("sess-1");
        assertThat(result.status()).isEqualTo(RUNNING);
        assertThat(result.finalMessage()).isNull();
    }

    @Test
    void pollSession_extractsFinalMessageOnCompletion() {
        doReturn(new BlocksAdapter.PollSessionResponse("sess-2", "completed", "PR opened successfully", null, null))
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
        doReturn(new BlocksAdapter.PollSessionResponse("sess-3", "running", null, null, null))
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

    // ---- fetchAssistantMessages ----

    @Test
    void fetchAssistantMessages_returnsNullWhenResponseIsEmpty() {
        doReturn(new BlocksAdapter.GetMessagesResponse(List.of()))
                .when(responseSpec).body(BlocksAdapter.GetMessagesResponse.class);

        assertThat(adapter.fetchAssistantMessages("sess-1")).isNull();
    }

    @Test
    void fetchAssistantMessages_returnsNullOnException() {
        doThrow(new RuntimeException("network error"))
                .when(responseSpec).body(BlocksAdapter.GetMessagesResponse.class);

        assertThat(adapter.fetchAssistantMessages("sess-err")).isNull();
    }

    @Test
    void fetchAssistantMessages_returnsAssistantMessageMessages() {
        var items = List.of(
                new BlocksAdapter.SessionMessage("1", "assistant", "message", "Working on it...", null),
                new BlocksAdapter.SessionMessage("2", "user", "message", "Please fix it", null)
        );
        doReturn(new BlocksAdapter.GetMessagesResponse(items))
                .when(responseSpec).body(BlocksAdapter.GetMessagesResponse.class);

        assertThat(adapter.fetchAssistantMessages("sess-1"))
                .containsExactly("Working on it...");
    }

    @Test
    void fetchAssistantMessages_includesErrorTypeMessages() {
        // Error-type messages carry token-limit notices from providers (e.g. rate_limit_error)
        var items = List.of(
                new BlocksAdapter.SessionMessage("1", "assistant", "message", "Analyzing the issue...", null),
                new BlocksAdapter.SessionMessage("2", "assistant", "error", "Rate limit reached for claude-3-5-sonnet", null)
        );
        doReturn(new BlocksAdapter.GetMessagesResponse(items))
                .when(responseSpec).body(BlocksAdapter.GetMessagesResponse.class);

        assertThat(adapter.fetchAssistantMessages("sess-1"))
                .containsExactly("Analyzing the issue...", "Rate limit reached for claude-3-5-sonnet");
    }

    @Test
    void fetchAssistantMessages_includesTextTypeMessages() {
        // Some Blocks API versions use type="text" as an alias for type="message"
        var items = List.of(
                new BlocksAdapter.SessionMessage("1", "assistant", "text", "Implementing fix...", null),
                new BlocksAdapter.SessionMessage("2", "assistant", "message", "Done.", null)
        );
        doReturn(new BlocksAdapter.GetMessagesResponse(items))
                .when(responseSpec).body(BlocksAdapter.GetMessagesResponse.class);

        assertThat(adapter.fetchAssistantMessages("sess-1"))
                .containsExactly("Implementing fix...", "Done.");
    }

    @Test
    void fetchAssistantMessages_excludesBlankMessages() {
        var items = List.of(
                new BlocksAdapter.SessionMessage("1", "assistant", "message", "Good message", null),
                new BlocksAdapter.SessionMessage("2", "assistant", "message", "   ", null),
                new BlocksAdapter.SessionMessage("3", "assistant", "message", null, null)
        );
        doReturn(new BlocksAdapter.GetMessagesResponse(items))
                .when(responseSpec).body(BlocksAdapter.GetMessagesResponse.class);

        assertThat(adapter.fetchAssistantMessages("sess-1"))
                .containsExactly("Good message");
    }

    @Test
    void fetchAssistantMessages_excludesToolUseAndToolResultMessages() {
        // Tool messages should not clutter the session message feed
        var items = List.of(
                new BlocksAdapter.SessionMessage("1", "assistant", "message", "Let me check.", null),
                new BlocksAdapter.SessionMessage("2", "assistant", "tool_use", "{\"name\":\"bash\",\"input\":{}}", null),
                new BlocksAdapter.SessionMessage("3", "tool", "tool_result", "$ ls\nfile.txt", null)
        );
        doReturn(new BlocksAdapter.GetMessagesResponse(items))
                .when(responseSpec).body(BlocksAdapter.GetMessagesResponse.class);

        assertThat(adapter.fetchAssistantMessages("sess-1"))
                .containsExactly("Let me check.");
    }

    // ---- helpers ----

    private SessionRequest minimalRequest(String issueId, String issueIdentifier, String title, String description) {
        return new SessionRequest(
                issueId, issueIdentifier, title, description,
                null, null, null, List.of(),
                null, null,
                null, null, null, null, null,
                List.of(),
                "test-key",
                null,
                null,
                null,
                null,
                List.of(),
                null
        );
    }
}
