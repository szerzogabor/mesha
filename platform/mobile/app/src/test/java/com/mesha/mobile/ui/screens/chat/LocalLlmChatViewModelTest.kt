package com.mesha.mobile.ui.screens.chat

import app.cash.turbine.test
import com.mesha.mobile.data.local.chat.ChatRepository
import com.mesha.mobile.domain.ai.AiProviderChoice
import com.mesha.mobile.domain.ai.AiProviderCoordinator
import com.mesha.mobile.domain.ai.AiProviderKeys
import com.mesha.mobile.domain.ai.AiProviderKind
import com.mesha.mobile.domain.ai.LocalAiException
import com.mesha.mobile.domain.ai.LocalChatMessage
import com.mesha.mobile.domain.ai.agent.AgentStep
import com.mesha.mobile.domain.ai.agent.TicketAgent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalLlmChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun agentReturning(vararg replies: String): TicketAgent {
        val agent = mockk<TicketAgent>()
        var index = 0
        coEvery { agent.run(any(), any()) } coAnswers {
            replies.getOrElse(index++) { replies.last() }
        }
        return agent
    }

    /**
     * A coordinator whose options are non-empty when [available], driving the ViewModel's
     * `modelAvailable`. The ViewModel observes options/selected and calls refresh() on init.
     */
    private fun availableProvider(available: Boolean = true): AiProviderCoordinator {
        val coordinator = mockk<AiProviderCoordinator>(relaxed = true)
        val opts = if (available) {
            listOf(AiProviderChoice(AiProviderKeys.LOCAL, "On-device", AiProviderKind.LOCAL))
        } else {
            emptyList()
        }
        every { coordinator.options } returns MutableStateFlow(opts)
        every { coordinator.selected } returns MutableStateFlow(opts.firstOrNull())
        coEvery { coordinator.refresh() } just Runs
        return coordinator
    }

    private fun emptyChatRepository(): ChatRepository {
        val repo = mockk<ChatRepository>(relaxed = true)
        coEvery { repo.loadMessages() } returns emptyList()
        return repo
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_modelAvailableCheckedOnInit() = runTest {
        val viewModel = LocalLlmChatViewModel(agentReturning("hi"), availableProvider(true), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.modelAvailable)
            assertTrue(state.entries.isEmpty())
            assertEquals("", state.inputText)
            assertFalse(state.isGenerating)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initialState_modelUnavailable_whenProviderReportsFalse() = runTest {
        val viewModel = LocalLlmChatViewModel(agentReturning("hi"), availableProvider(false), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            assertFalse(awaitItem().modelAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onInputChange_updatesInputText() = runTest {
        val viewModel = LocalLlmChatViewModel(agentReturning("hi"), availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello!")

        viewModel.state.test {
            assertEquals("Hello!", awaitItem().inputText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_addsUserAndAssistantEntries() = runTest {
        val viewModel = LocalLlmChatViewModel(agentReturning("Hi there!"), availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.entries.size)
            assertEquals(ChatEntry.User("Hello"), state.entries[0])
            assertEquals(ChatEntry.Assistant("Hi there!"), state.entries[1])
            assertFalse(state.isGenerating)
            assertEquals("", state.inputText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_surfacesToolActivityFromAgentSteps() = runTest {
        val agent = mockk<TicketAgent>()
        coEvery { agent.run(any(), any()) } coAnswers {
            val onStep = secondArg<(AgentStep) -> Unit>()
            onStep(AgentStep.ToolInvocation("list_tickets", "list_tickets"))
            onStep(AgentStep.ToolResult("list_tickets", "2 tickets: MES-1, MES-2"))
            "You have 2 tickets."
        }
        val viewModel = LocalLlmChatViewModel(agent, availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("what's open?")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val entries = awaitItem().entries
            assertEquals(3, entries.size)
            assertEquals(ChatEntry.User("what's open?"), entries[0])
            val tool = entries[1] as ChatEntry.Tool
            assertEquals("list_tickets", tool.title)
            assertEquals("2 tickets: MES-1, MES-2", tool.detail)
            assertFalse(tool.running)
            assertEquals(ChatEntry.Assistant("You have 2 tickets."), entries[2])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_passesGrowingConversationToAgent() = runTest {
        val histories = mutableListOf<List<LocalChatMessage>>()
        val agent = mockk<TicketAgent>()
        var index = 0
        coEvery { agent.run(capture(histories), any()) } coAnswers {
            listOf("Reply 1", "Reply 2").getOrElse(index++) { "Reply" }
        }
        val viewModel = LocalLlmChatViewModel(agent, availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("First message")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Second message")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // First turn: [user]. Second turn: [user, assistant, user].
        assertEquals(1, histories[0].size)
        assertEquals(3, histories[1].size)
        assertEquals(LocalChatMessage.Role.USER, histories[1][0].role)
        assertEquals(LocalChatMessage.Role.ASSISTANT, histories[1][1].role)
        assertEquals("Reply 1", histories[1][1].content)
        assertEquals(LocalChatMessage.Role.USER, histories[1][2].role)
    }

    @Test
    fun sendMessage_ignoresBlankInput() = runTest {
        val agent = agentReturning("unused")
        val viewModel = LocalLlmChatViewModel(agent, availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("   ")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            assertTrue(awaitItem().entries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { agent.run(any(), any()) }
    }

    @Test
    fun sendMessage_ignoresWhenAlreadyGenerating() = runTest {
        val agent = agentReturning("Response")
        val viewModel = LocalLlmChatViewModel(agent, availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("First")
        viewModel.sendMessage()
        // Don't advance - still generating

        viewModel.onInputChange("Second")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { agent.run(any(), any()) }
    }

    @Test
    fun sendMessage_setsErrorOnLocalAiException() = runTest {
        val agent = mockk<TicketAgent>()
        coEvery { agent.run(any(), any()) } throws LocalAiException.ModelNotAvailable("Model gone")
        val viewModel = LocalLlmChatViewModel(agent, availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.error?.isNotBlank() == true)
            assertFalse(state.isGenerating)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_setsErrorOnGenericException() = runTest {
        val agent = mockk<TicketAgent>()
        coEvery { agent.run(any(), any()) } throws RuntimeException("Network error")
        val viewModel = LocalLlmChatViewModel(agent, availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.error?.contains("Network error") == true)
            assertFalse(state.isGenerating)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dismissError_clearsErrorField() = runTest {
        val agent = mockk<TicketAgent>()
        coEvery { agent.run(any(), any()) } throws LocalAiException.InferenceFailed("oops")
        val viewModel = LocalLlmChatViewModel(agent, availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("hi")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissError()

        viewModel.state.test {
            assertNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearSession_resetsEntriesAndConversation() = runTest {
        val viewModel = LocalLlmChatViewModel(agentReturning("Hi there!"), availableProvider(), emptyChatRepository())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearSession()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.entries.isEmpty())
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearSession_persistedMessagesRestoredOnInit() = runTest {
        val repo = mockk<ChatRepository>(relaxed = true)
        coEvery { repo.loadMessages() } returns listOf(
            LocalChatMessage(LocalChatMessage.Role.USER, "Stored question"),
            LocalChatMessage(LocalChatMessage.Role.ASSISTANT, "Stored answer"),
        )
        val viewModel = LocalLlmChatViewModel(agentReturning("hi"), availableProvider(), repo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.entries.size)
            assertEquals(ChatEntry.User("Stored question"), state.entries[0])
            assertEquals(ChatEntry.Assistant("Stored answer"), state.entries[1])
            cancelAndIgnoreRemainingEvents()
        }
    }
}
