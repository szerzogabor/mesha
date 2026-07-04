package com.mesha.mobile.domain.ai.agent

import com.mesha.mobile.data.repository.SelectionStore
import com.mesha.mobile.domain.ai.AiProviderCoordinator
import com.mesha.mobile.domain.ai.GenerateIssueRequest
import com.mesha.mobile.domain.ai.IssueDraft
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.ai.LocalChatMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketAgentTest {

    /** A [LocalAiProvider] that returns a scripted response for each successive [generate] call. */
    private class ScriptedProvider(private val responses: List<String>) : LocalAiProvider {
        var calls = 0
            private set
        override val id = "scripted"
        override val displayName = "Scripted"
        override suspend fun isAvailable() = true
        override suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft =
            throw UnsupportedOperationException()
        override suspend fun generateChatResponse(history: List<LocalChatMessage>): String =
            throw UnsupportedOperationException()
        override suspend fun generate(prompt: String): String =
            responses.getOrElse(calls++) { responses.last() }
    }

    /** A tool that records its invocations and returns a fixed observation. */
    private class RecordingTool(
        override val name: String,
        private val observation: String,
    ) : AgentTool {
        var invocations = 0
            private set
        override val description = "test tool"
        override val argsSpec = "{}"
        override suspend fun execute(args: JsonObject, context: AgentContext): String {
            invocations++
            return observation
        }
    }

    private fun selection(): SelectionStore = SelectionStore().apply {
        selectWorkspace("w")
        selectProject("p")
    }

    /** Wraps a provider in a coordinator whose active() returns it — the agent only calls active(). */
    private fun coordinatorFor(provider: LocalAiProvider): AiProviderCoordinator {
        val coordinator = mockk<AiProviderCoordinator>()
        every { coordinator.active() } returns provider
        return coordinator
    }

    private fun registryWith(vararg tools: AgentTool): AgentToolRegistry {
        val registry = mockk<AgentToolRegistry>()
        every { registry.tools } returns tools.toList()
        every { registry.find(any()) } answers {
            val requested = firstArg<String>().lowercase()
            tools.firstOrNull { it.name.lowercase() == requested }
        }
        return registry
    }

    private fun history(text: String) = listOf(LocalChatMessage(LocalChatMessage.Role.USER, text))

    @Test
    fun executes_tool_then_returns_final_answer() = runTest {
        val tool = RecordingTool("list_tickets", "2 tickets: MES-1, MES-2")
        val provider = ScriptedProvider(
            listOf(
                """{"tool":"list_tickets","arguments":{}}""",
                """{"final":"You have 2 tickets."}""",
            ),
        )
        val agent = TicketAgent(coordinatorFor(provider), registryWith(tool), selection())

        val steps = mutableListOf<AgentStep>()
        val reply = agent.run(history("what's open?")) { steps.add(it) }

        assertEquals("You have 2 tickets.", reply)
        assertEquals(1, tool.invocations)
        assertTrue(steps.any { it is AgentStep.ToolInvocation && it.tool == "list_tickets" })
        assertTrue(steps.any { it is AgentStep.ToolResult && it.observation.contains("MES-1") })
    }

    @Test
    fun plain_prose_answer_ends_immediately_without_tools() = runTest {
        val tool = RecordingTool("list_tickets", "unused")
        val provider = ScriptedProvider(listOf("Hello! How can I help with your tickets?"))
        val agent = TicketAgent(coordinatorFor(provider), registryWith(tool), selection())

        val reply = agent.run(history("hi"))

        assertEquals("Hello! How can I help with your tickets?", reply)
        assertEquals(0, tool.invocations)
    }

    @Test
    fun unknown_tool_is_reported_back_then_agent_recovers() = runTest {
        val tool = RecordingTool("list_tickets", "1 ticket")
        val provider = ScriptedProvider(
            listOf(
                """{"tool":"delete_everything","arguments":{}}""",
                """{"final":"I can't do that, but you have 1 ticket."}""",
            ),
        )
        val agent = TicketAgent(coordinatorFor(provider), registryWith(tool), selection())

        val steps = mutableListOf<AgentStep>()
        val reply = agent.run(history("nuke it")) { steps.add(it) }

        assertEquals("I can't do that, but you have 1 ticket.", reply)
        assertEquals(0, tool.invocations)
        val result = steps.filterIsInstance<AgentStep.ToolResult>().first()
        assertTrue(result.observation.contains("Unknown tool"))
    }

    @Test
    fun step_budget_exhaustion_returns_last_observation() = runTest {
        val tool = RecordingTool("list_tickets", "still working")
        // Always calls a tool, never finalizes.
        val provider = ScriptedProvider(listOf("""{"tool":"list_tickets","arguments":{}}"""))
        val agent = TicketAgent(coordinatorFor(provider), registryWith(tool), selection())

        val reply = agent.run(history("loop forever"))

        assertTrue(reply.contains("still working"))
        // MAX_STEPS invocations, not infinite.
        assertEquals(6, tool.invocations)
    }
}
