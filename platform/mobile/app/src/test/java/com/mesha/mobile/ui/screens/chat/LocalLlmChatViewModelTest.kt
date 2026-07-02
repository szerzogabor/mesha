package com.mesha.mobile.ui.screens.chat

import app.cash.turbine.test
import com.mesha.mobile.domain.ai.LocalAiException
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.ai.LocalChatMessage
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
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns true

        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.modelAvailable)
            assertTrue(state.messages.isEmpty())
            assertEquals("", state.inputText)
            assertFalse(state.isGenerating)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initialState_modelUnavailable_whenProviderReportsFalse() = runTest {
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns false

        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            assertFalse(awaitItem().modelAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onInputChange_updatesInputText() = runTest {
        val localAi = mockk<LocalAiProvider>(relaxed = true)
        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello!")

        viewModel.state.test {
            assertEquals("Hello!", awaitItem().inputText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onInputChange_clearsExistingError() = runTest {
        val localAi = mockk<LocalAiProvider>(relaxed = true)
        coEvery { localAi.generateChatResponse(any()) } throws LocalAiException.InferenceFailed("failed")
        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("trigger error")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("new input")

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_addsUserAndAssistantMessages() = runTest {
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns true
        coEvery { localAi.generateChatResponse(any()) } returns "Hi there!"

        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Hello")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.messages.size)
            assertEquals(LocalChatMessage.Role.USER, state.messages[0].role)
            assertEquals("Hello", state.messages[0].content)
            assertEquals(LocalChatMessage.Role.ASSISTANT, state.messages[1].role)
            assertEquals("Hi there!", state.messages[1].content)
            assertFalse(state.isGenerating)
            assertEquals("", state.inputText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_passesFullHistoryToProvider() = runTest {
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns true
        coEvery { localAi.generateChatResponse(any()) } returnsMany listOf("Reply 1", "Reply 2")

        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("First message")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("Second message")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            localAi.generateChatResponse(match { history ->
                history.size == 3 &&
                    history[0].role == LocalChatMessage.Role.USER &&
                    history[1].role == LocalChatMessage.Role.ASSISTANT &&
                    history[2].role == LocalChatMessage.Role.USER
            })
        }
    }

    @Test
    fun sendMessage_ignoresBlankInput() = runTest {
        val localAi = mockk<LocalAiProvider>(relaxed = true)
        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("   ")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.messages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { localAi.generateChatResponse(any()) }
    }

    @Test
    fun sendMessage_ignoresWhenAlreadyGenerating() = runTest {
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns true
        coEvery { localAi.generateChatResponse(any()) } returns "Response"

        val viewModel = LocalLlmChatViewModel(localAi)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChange("First")
        viewModel.sendMessage()
        // Don't advance - still generating

        viewModel.onInputChange("Second")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { localAi.generateChatResponse(any()) }
    }

    @Test
    fun sendMessage_setsErrorOnLocalAiException() = runTest {
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns true
        coEvery { localAi.generateChatResponse(any()) } throws
            LocalAiException.ModelNotAvailable("Model gone")

        val viewModel = LocalLlmChatViewModel(localAi)
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
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns true
        coEvery { localAi.generateChatResponse(any()) } throws RuntimeException("Network error")

        val viewModel = LocalLlmChatViewModel(localAi)
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
        val localAi = mockk<LocalAiProvider>()
        coEvery { localAi.isAvailable() } returns true
        coEvery { localAi.generateChatResponse(any()) } throws LocalAiException.InferenceFailed("oops")

        val viewModel = LocalLlmChatViewModel(localAi)
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
