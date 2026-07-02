package com.mesha.mobile.ui.screens.issues

import app.cash.turbine.test
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.repository.MeshaRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IssueDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MeshaRepository
    private lateinit var viewModel: IssueDetailViewModel

    private val projectId = "project-1"
    private val issueId = "issue-1"

    private val issueDto = IssueDto(
        id = issueId,
        projectId = projectId,
        identifier = "TP-87",
        title = "Fix ticket navigation",
        description = "Ticket not opened when clicked",
        status = "IN_PROGRESS",
        priority = "URGENT",
    )

    private val commentDto = CommentDto(
        id = "comment-1",
        issueId = issueId,
        body = "Looking into this",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = IssueDetailViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        assertTrue(viewModel.state.value.loading)
        assertNull(viewModel.state.value.issue)
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.comments.isEmpty())
    }

    @Test
    fun `load success populates issue and comments`() = runTest {
        coEvery { repository.getIssue(projectId, issueId) } returns Result.success(issueDto)
        coEvery { repository.getComments(issueId) } returns Result.success(listOf(commentDto))

        viewModel.state.test {
            awaitItem() // initial loading state

            viewModel.load(projectId, issueId)
            testDispatcher.scheduler.advanceUntilIdle()

            val loaded = awaitItem()
            assertFalse(loaded.loading)
            assertNull(loaded.error)
            assertNotNull(loaded.issue)
            assertEquals("TP-87", loaded.issue?.identifier)
            assertEquals(1, loaded.comments.size)
            assertEquals("Looking into this", loaded.comments.first().body)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load with null issue emits error`() = runTest {
        coEvery { repository.getIssue(projectId, issueId) } returns Result.failure(RuntimeException("not found"))
        coEvery { repository.getComments(issueId) } returns Result.success(emptyList())

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.load(projectId, issueId)
            testDispatcher.scheduler.advanceUntilIdle()

            val error = awaitItem()
            assertFalse(error.loading)
            assertNotNull(error.error)
            assertNull(error.issue)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load shows issue even when comments fail`() = runTest {
        coEvery { repository.getIssue(projectId, issueId) } returns Result.success(issueDto)
        coEvery { repository.getComments(issueId) } returns Result.failure(RuntimeException("timeout"))

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.load(projectId, issueId)
            testDispatcher.scheduler.advanceUntilIdle()

            val loaded = awaitItem()
            assertFalse(loaded.loading)
            assertNotNull(loaded.issue)
            assertTrue(loaded.comments.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
