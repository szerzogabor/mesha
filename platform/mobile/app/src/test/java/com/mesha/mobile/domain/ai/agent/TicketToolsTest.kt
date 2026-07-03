package com.mesha.mobile.domain.ai.agent

import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.UpdateIssueRequestDto
import com.mesha.mobile.data.remote.dto.UserDto
import com.mesha.mobile.data.remote.dto.WorkspaceMemberDto
import com.mesha.mobile.data.repository.MeshaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketToolsTest {

    private val projectCtx = AgentContext(workspaceId = "w", projectId = "p")

    private fun args(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private val issueMes1 = IssueDto(
        id = "i1",
        projectId = "p",
        identifier = "MES-1",
        title = "Login is broken",
        description = "Users can't sign in",
        status = "Todo",
        priority = "HIGH",
        assignee = null,
        labels = emptyList(),
    )

    @Test
    fun getTicket_resolvesByIdentifier() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))

        val result = GetTicketTool(repo).execute(args("""{"ticket":"mes-1"}"""), projectCtx)

        assertTrue(result.contains("MES-1"))
        assertTrue(result.contains("Login is broken"))
    }

    @Test
    fun getTicket_resolvesByTitleSubstring() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))

        val result = GetTicketTool(repo).execute(args("""{"ticket":"login"}"""), projectCtx)

        assertTrue(result.contains("MES-1"))
    }

    @Test
    fun getTicket_reportsWhenNotFound() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(emptyList())

        val result = GetTicketTool(repo).execute(args("""{"ticket":"MES-99"}"""), projectCtx)

        assertTrue(result.contains("No ticket found"))
    }

    @Test
    fun tool_reportsWhenNoProjectSelected() = runTest {
        val repo = mockk<MeshaRepository>()
        val result = GetTicketTool(repo).execute(
            args("""{"ticket":"MES-1"}"""),
            AgentContext(workspaceId = "w", projectId = null),
        )
        assertTrue(result.contains("No project is selected"))
    }

    @Test
    fun updateTicket_resolvesStatusCaseInsensitively() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))
        coEvery { repo.getProjectStatuses("p") } returns
            Result.success(listOf(ProjectStatusDto(id = "s1", name = "Done", position = 3)))
        val body = slot<UpdateIssueRequestDto>()
        coEvery { repo.updateIssue("p", "i1", capture(body)) } returns Result.success(issueMes1)

        val result = UpdateTicketTool(repo).execute(
            args("""{"ticket":"MES-1","status":"done"}"""),
            projectCtx,
        )

        assertEquals("Done", body.captured.status)
        assertTrue(result.contains("Updated MES-1"))
        assertTrue(result.contains("status"))
    }

    @Test
    fun updateTicket_rejectsUnknownStatus() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))
        coEvery { repo.getProjectStatuses("p") } returns
            Result.success(listOf(ProjectStatusDto(id = "s1", name = "Done", position = 3)))

        val result = UpdateTicketTool(repo).execute(
            args("""{"ticket":"MES-1","status":"Shipped"}"""),
            projectCtx,
        )

        assertTrue(result.contains("doesn't exist"))
        coVerify(exactly = 0) { repo.updateIssue(any(), any(), any()) }
    }

    @Test
    fun updateTicket_rejectsInvalidPriority() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))

        val result = UpdateTicketTool(repo).execute(
            args("""{"ticket":"MES-1","priority":"SOON"}"""),
            projectCtx,
        )

        assertTrue(result.contains("Priority must be one of"))
        coVerify(exactly = 0) { repo.updateIssue(any(), any(), any()) }
    }

    @Test
    fun updateTicket_clearsAssigneeOnNone() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))
        val body = slot<UpdateIssueRequestDto>()
        coEvery { repo.updateIssue("p", "i1", capture(body)) } returns Result.success(issueMes1)

        UpdateTicketTool(repo).execute(args("""{"ticket":"MES-1","assignee":"none"}"""), projectCtx)

        assertEquals(true, body.captured.clearAssignee)
    }

    @Test
    fun updateTicket_replacesLabelsAndSkipsUnknown() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))
        coEvery { repo.getLabels("w") } returns Result.success(
            listOf(LabelDto(id = "l1", name = "bug"), LabelDto(id = "l2", name = "backend")),
        )
        val body = slot<UpdateIssueRequestDto>()
        coEvery { repo.updateIssue("p", "i1", capture(body)) } returns Result.success(issueMes1)

        val result = UpdateTicketTool(repo).execute(
            args("""{"ticket":"MES-1","labels":["bug","nonexistent"]}"""),
            projectCtx,
        )

        assertEquals(listOf("l1"), body.captured.labelIds)
        assertTrue(result.contains("skipped unknown labels: nonexistent"))
    }

    @Test
    fun updateTicket_abortsWhenLabelFetchFails_ratherThanClearing() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))
        coEvery { repo.getLabels("w") } returns Result.failure(RuntimeException("offline"))

        val result = UpdateTicketTool(repo).execute(
            args("""{"ticket":"MES-1","labels":["bug"]}"""),
            projectCtx,
        )

        assertTrue(result.contains("Couldn't load labels"))
        // Must NOT fall through to an update that would wipe the ticket's labels.
        coVerify(exactly = 0) { repo.updateIssue(any(), any(), any()) }
    }

    @Test
    fun updateTicket_resolvesAssigneeByName() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))
        coEvery { repo.getWorkspaceMembers("w") } returns Result.success(
            listOf(WorkspaceMemberDto(id = "m1", userId = "u1", email = "a@b.com", name = "Alice")),
        )
        val body = slot<UpdateIssueRequestDto>()
        coEvery { repo.updateIssue("p", "i1", capture(body)) } returns Result.success(issueMes1)

        UpdateTicketTool(repo).execute(args("""{"ticket":"MES-1","assignee":"Alice"}"""), projectCtx)

        assertEquals("u1", body.captured.assigneeId)
    }

    @Test
    fun addComment_resolvesTicketAndPostsBody() = runTest {
        val repo = mockk<MeshaRepository>()
        coEvery { repo.getIssues(any(), any(), any()) } returns Result.success(listOf(issueMes1))
        coEvery { repo.addComment("i1", "Looks good", null) } returns
            Result.success(CommentDto(id = "c1", issueId = "i1", body = "Looks good", author = UserDto("u1")))

        val result = AddCommentTool(repo).execute(
            args("""{"ticket":"MES-1","body":"Looks good"}"""),
            projectCtx,
        )

        assertTrue(result.contains("Added a comment to MES-1"))
        coVerify { repo.addComment("i1", "Looks good", null) }
    }
}
