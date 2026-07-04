package com.mesha.mobile.ui.screens.issues

import app.cash.turbine.test
import com.mesha.mobile.data.remote.AttachmentOpener
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.IssueAttachmentDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IssueDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MeshaRepository
    private lateinit var attachmentOpener: AttachmentOpener
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
        // Best-effort side loads performed by load() — default them to empty so tests
        // can focus on the issue + comments behaviour.
        coEvery { repository.getProjectStatuses(any()) } returns Result.success(emptyList())
        coEvery { repository.getIssueActivity(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.getIssueAgents(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.getBlocksSessions(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.getIssueAttachments(any(), any()) } returns Result.success(emptyList())
        attachmentOpener = mockk(relaxed = true)
        viewModel = IssueDetailViewModel(repository, SelectionStore(), attachmentOpener)
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
    fun `load populates attachments`() = runTest {
        val attachment = IssueAttachmentDto(
            id = "att-1",
            issueId = issueId,
            fileName = "screenshot.png",
            contentType = "image/png",
            fileSize = 2048,
        )
        coEvery { repository.getIssue(projectId, issueId) } returns Result.success(issueDto)
        coEvery { repository.getComments(issueId) } returns Result.success(emptyList())
        coEvery { repository.getIssueAttachments(projectId, issueId) } returns Result.success(listOf(attachment))

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.load(projectId, issueId)
            testDispatcher.scheduler.advanceUntilIdle()

            val loaded = awaitItem()
            assertEquals(1, loaded.attachments.size)
            assertEquals("screenshot.png", loaded.attachments.first().fileName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openAttachment downloads through the opener and clears progress`() = runTest {
        val attachment = IssueAttachmentDto(
            id = "att-1",
            issueId = issueId,
            fileName = "doc.pdf",
            contentType = "application/pdf",
            fileSize = 1024,
        )
        coEvery { repository.getIssue(projectId, issueId) } returns Result.success(issueDto)
        coEvery { repository.getComments(issueId) } returns Result.success(emptyList())

        viewModel.load(projectId, issueId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openAttachment(attachment)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { attachmentOpener.open(any(), "doc.pdf", "application/pdf") }
        assertNull(viewModel.state.value.openingAttachmentId)
        assertNull(viewModel.state.value.updateError)
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
