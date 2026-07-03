package com.mesha.api.service;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.AutomationTriggerType;
import com.mesha.api.model.BlocksMessage;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.Issue;
import com.mesha.api.repository.BlocksMessageRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.worker.blocks.BlocksAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the webhook state-update path fires the {@code AI_TOKEN_LIMIT_HIT} automation
 * trigger when the terminating provider message indicates a token/usage limit — the gap that
 * previously left token-limit rules unfired for webhook-driven terminal transitions.
 */
class BlocksSessionServiceTokenLimitTest {

    @Mock private BlocksSessionRepository blocksSessionRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private ActivityService activityService;
    @Mock private BlocksConfigService blocksConfigService;
    @Mock private BlocksMessageRepository blocksMessageRepository;
    @Mock private BlocksAdapter blocksAdapter;
    @Mock private GitHubPullRequestRepository gitHubPullRequestRepository;
    @Mock private AutomationService automationService;
    @Mock private TicketRuleService ticketRuleService;

    private BlocksSessionService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new BlocksSessionService(blocksSessionRepository, issueRepository, activityService,
                blocksConfigService, blocksMessageRepository, blocksAdapter, gitHubPullRequestRepository,
                automationService, ticketRuleService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private BlocksSession activeSession() {
        Issue issue = new Issue();
        BlocksSession session = new BlocksSession();
        session.setIssue(issue);
        session.setExecutionState(AIExecutionState.EXECUTING);
        session.setProviderSessionId("prov-123");
        when(blocksSessionRepository.findFirstByProviderSessionIdOrderByCreatedAtDesc("prov-123"))
                .thenReturn(Optional.of(session));
        when(blocksSessionRepository.save(any(BlocksSession.class))).thenAnswer(inv -> inv.getArgument(0));
        return session;
    }

    @Test
    void webhookFailure_withTokenLimitErrorMessage_firesTokenLimitTrigger() {
        activeSession();

        service.handleWebhookStateUpdate("prov-123", AIExecutionState.FAILED, null, null, null,
                "You've hit your limit · resets 4:40pm (UTC)", null);

        verify(automationService).executeFor(eq(AutomationTriggerType.BLOCKS_SESSION_FAILED), any());
        verify(automationService).executeFor(eq(AutomationTriggerType.AI_TOKEN_LIMIT_HIT), any());
    }

    @Test
    void webhookFailure_withCopilotAllowanceMessage_firesTokenLimitTrigger() {
        activeSession();

        service.handleWebhookStateUpdate("prov-123", AIExecutionState.FAILED, null, null, null,
                "You have exceeded your premium request allowance.", null);

        verify(automationService).executeFor(eq(AutomationTriggerType.AI_TOKEN_LIMIT_HIT), any());
    }

    @Test
    void webhookFailure_withTokenLimitInSavedMessage_firesTokenLimitTrigger() {
        BlocksSession session = activeSession();
        BlocksMessage msg = new BlocksMessage();
        msg.setSession(session);
        msg.setMessage("429 RESOURCE_EXHAUSTED: Quota exceeded for quota metric");
        when(blocksMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()))
                .thenReturn(List.of(msg));

        service.handleWebhookStateUpdate("prov-123", AIExecutionState.FAILED, null, null, null, null, null);

        verify(automationService).executeFor(eq(AutomationTriggerType.AI_TOKEN_LIMIT_HIT), any());
    }

    @Test
    void webhookCompletion_withoutTokenLimit_doesNotFireTokenLimitTrigger() {
        BlocksSession session = activeSession();
        when(blocksMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()))
                .thenReturn(List.of());

        service.handleWebhookStateUpdate("prov-123", AIExecutionState.DONE, null, null, null, null, null);

        verify(automationService).executeFor(eq(AutomationTriggerType.BLOCKS_SESSION_COMPLETED), any());
        verify(automationService, never()).executeFor(eq(AutomationTriggerType.AI_TOKEN_LIMIT_HIT), any());
    }
}
