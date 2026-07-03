package com.mesha.mobile.ui.screens.chat

import app.cash.turbine.test
import com.mesha.mobile.domain.ai.LocalAiException
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.ai.LocalChatMessage
import com.mesha.mobile.domain.ai.agent.AgentStep
import com.mesha.mobile.domain.ai.agent.TicketAgent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

    private fun availableProvider(available: Boolean = true): LocalAiProvider {
        val provider = mockk<LocalAiProvider>(relaxed = true)
        coEvery { provider.isAvailable() } returns available
        return provider
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
        val viewModel = LocalLlmChatViewModel(agentReturning("hi"), availableProvider(true))
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
        val viewModel = LocalLlmChatViewModel(agentReturning("hi"), availableProvider(false))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            assertFalse(awaitItem().modelAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onInputChange_updatesInputText() = runTest {
        val viewModel = LocalLlmChatViewModel(agentReturning("hi"), availableProvider())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello!")

        viewModel.state.test {
            assertEquals("Hello!", awaitItem().inputText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_addsUserAndAssistantEntries() = runTest {
        val viewModel = LocalLlmChatViewModel(agentReturning("Hi there!"), availableProvider())
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
        val viewModel = LocalLlmChatViewModel(agent, availableProvider())
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
        val viewModel = LocalLlmChatViewModel(agent, availableProvider())
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
        val viewModel = LocalLlmChatViewModel(agent, availableProvider())
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
        val viewModel = LocalLlmChatViewModel(agent, availableProvider())
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
        val viewModel = LocalLlmChatViewModel(agent, availableProvider())
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
        val viewModel = LocalLlmChatViewModel(agent, availableProvider())
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
        val viewModel = LocalLlmChatViewModel(agent, availableProvider())
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
}
