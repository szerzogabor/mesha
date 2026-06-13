package com.mesha.api.worker.scheduling;

import com.mesha.api.repository.BlocksMessageRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.CommentRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.repository.WorkspaceBlocksConfigRepository;
import com.mesha.api.service.AutomationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPollTransactionsTokenLimitTest {

    @Mock private BlocksSessionRepository sessionRepo;
    @Mock private IssueRepository issueRepo;
    @Mock private CommentRepository commentRepo;
    @Mock private GitHubRepositoryRepository gitHubRepoRepo;
    @Mock private BlocksMessageRepository messageRepo;
    @Mock private WorkspaceBlocksConfigRepository configRepo;
    @Mock private BlocksApiKeyService apiKeyService;
    @Mock private AutomationService automationService;

    private SessionPollTransactions txns;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        txns = new SessionPollTransactions(sessionRepo, issueRepo, commentRepo,
                gitHubRepoRepo, messageRepo, configRepo, apiKeyService, automationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void isTokenLimitMessage_returnsFalseForNull() {
        assertThat(txns.isTokenLimitMessage(null)).isFalse();
    }

    @Test
    void isTokenLimitMessage_returnsFalseForNonLimitMessage() {
        assertThat(txns.isTokenLimitMessage("Session completed successfully")).isFalse();
    }

    // Claude.ai: "You've hit your limit · resets 4:40pm (UTC)"
    @ParameterizedTest
    @ValueSource(strings = {
            "You've hit your limit · resets 4:40pm (UTC)",
            "You've hit your limit. Come back tomorrow.",
            "hit your limit",
            // Claude.ai alternate: runs out of messages in a conversation
            "You're out of messages until 4:40pm (UTC)",
            "out of messages",
    })
    void isTokenLimitMessage_detectsClaudeUsageLimitMessages(String message) {
        assertThat(txns.isTokenLimitMessage(message)).isTrue();
    }

    // Claude API: "prompt is too long" and rate limit errors
    @ParameterizedTest
    @ValueSource(strings = {
            "prompt is too long",
            "The prompt is too long for this model",
            // Claude API rate limiting: "rate_limit_error" / "Rate limit reached"
            "Rate limit reached for model claude-3-5-sonnet",
            "rate_limit_error: too many requests",
            "rate-limit exceeded",
    })
    void isTokenLimitMessage_detectsClaudeApiContextMessages(String message) {
        assertThat(txns.isTokenLimitMessage(message)).isTrue();
    }

    // Gemini: "The input token count (X) exceeds the maximum number of tokens allowed (Y)"
    // and quota/resource exhausted errors
    @ParameterizedTest
    @ValueSource(strings = {
            "The input token count (1624359) exceeds the maximum number of tokens allowed (1048576).",
            "maximum number of tokens allowed",
            "input token count exceeds limit",
            "API Error: Input Token Count Exceeds Maximum Number of Tokens Allowed",
            // Gemini quota/rate errors: HTTP 429 RESOURCE_EXHAUSTED
            "429 RESOURCE_EXHAUSTED: Quota exceeded for quota metric",
            "RESOURCE_EXHAUSTED",
            "resource_exhausted",
            "Resource has been exhausted (e.g. check quota).",
            "quota_exceeded: daily limit reached",
            "Quota exceeded for this project",
    })
    void isTokenLimitMessage_detectsGeminiTokenLimitMessages(String message) {
        assertThat(txns.isTokenLimitMessage(message)).isTrue();
    }

    // GitHub Copilot (ghagpt) / OpenAI / ChatGPT: various token and rate limit messages
    @ParameterizedTest
    @ValueSource(strings = {
            "Oops, the token limit exceeded. Try to shorten your prompt or start a new conversation.",
            "Note: I'm nearing the token limit for this thread",
            "prompt token count of 131835 exceeds the limit of 128000",
            "token count exceeds maximum",
            "This model's maximum context length is 8192 tokens",
            // OpenAI/ChatGPT quota errors: "insufficient_quota", "exceeded your current quota"
            "insufficient_quota: you have exceeded your current quota",
            "insufficient quota",
            // OpenAI rate limiting
            "Rate limit reached for gpt-4: 10000 tokens per minute",
    })
    void isTokenLimitMessage_detectsGitHubCopilotTokenLimitMessages(String message) {
        assertThat(txns.isTokenLimitMessage(message)).isTrue();
    }

    // Pre-existing generic patterns
    @ParameterizedTest
    @ValueSource(strings = {
            "token_limit reached",
            "context_limit exceeded",
            "context_length exceeded",
            "max_tokens exceeded",
            "out_of_tokens",
            "context_window overflow",
            "TOKEN LIMIT",
            "CONTEXT WINDOW",
    })
    void isTokenLimitMessage_detectsGenericPatterns(String message) {
        assertThat(txns.isTokenLimitMessage(message)).isTrue();
    }

    // anyMessageIsTokenLimit — verifies that the token limit is detected even when it is
    // not the last message in the list (the root cause of TP-42: only the final message
    // was checked, so a limit notice followed by a follow-up message was missed).

    @Test
    void anyMessageIsTokenLimit_returnsFalseForNull() {
        assertThat(txns.anyMessageIsTokenLimit(null)).isFalse();
    }

    @Test
    void anyMessageIsTokenLimit_returnsFalseForEmptyList() {
        assertThat(txns.anyMessageIsTokenLimit(List.of())).isFalse();
    }

    @Test
    void anyMessageIsTokenLimit_returnsFalseWhenNoLimitMessage() {
        assertThat(txns.anyMessageIsTokenLimit(
                List.of("Analyzing issue...", "Implementing fix...", "Done."))).isFalse();
    }

    @Test
    void anyMessageIsTokenLimit_detectsLimitInLastMessage() {
        assertThat(txns.anyMessageIsTokenLimit(
                List.of("Analyzing issue...", "You've hit your limit · resets 4:40pm (UTC)"))).isTrue();
    }

    @Test
    void anyMessageIsTokenLimit_detectsLimitWhenNotLastMessage() {
        // The token limit notice is followed by a closing message — only scanning all
        // messages (not just the last) catches it.
        List<String> messages = List.of(
                "Analyzing the codebase...",
                "You've hit your limit · resets 4:40pm (UTC)",
                "I was unable to complete the task due to the usage limit."
        );
        assertThat(txns.anyMessageIsTokenLimit(messages)).isTrue();
    }

    @Test
    void anyMessageIsTokenLimit_detectsGeminiLimitInMiddleOfList() {
        List<String> messages = List.of(
                "Let me look at the code.",
                "429 RESOURCE_EXHAUSTED: Quota exceeded for quota metric",
                "Please try again later."
        );
        assertThat(txns.anyMessageIsTokenLimit(messages)).isTrue();
    }
}
