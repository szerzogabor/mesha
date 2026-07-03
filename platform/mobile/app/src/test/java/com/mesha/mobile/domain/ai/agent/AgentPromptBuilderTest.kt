package com.mesha.mobile.domain.ai.agent

import com.mesha.mobile.domain.ai.LocalChatMessage
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptBuilderTest {

    private val tools = listOf(
        fakeTool("list_tickets", "List tickets."),
        fakeTool("update_ticket", "Update a ticket."),
    )

    private fun fakeTool(toolName: String, desc: String) = object : AgentTool {
        override val name = toolName
        override val description = desc
        override val argsSpec = "{}"
        override suspend fun execute(args: JsonObject, context: AgentContext) = "ok"
    }

    private fun userMsg(text: String) = LocalChatMessage(LocalChatMessage.Role.USER, text)
    private fun assistantMsg(text: String) = LocalChatMessage(LocalChatMessage.Role.ASSISTANT, text)

    @Test
    fun preamble_lists_tools_and_protocol() {
        val preamble = AgentPromptBuilder.preamble(tools, AgentContext("w", "p"))
        assertTrue(preamble.contains("list_tickets"))
        assertTrue(preamble.contains("update_ticket"))
        assertTrue(preamble.contains("\"tool\""))
        assertTrue(preamble.contains("\"final\""))
    }

    @Test
    fun preamble_notes_when_no_project_selected() {
        val withProject = AgentPromptBuilder.preamble(tools, AgentContext("w", "p"))
        val withoutProject = AgentPromptBuilder.preamble(tools, AgentContext("w", null))
        assertTrue(withProject.contains("project is currently selected"))
        assertTrue(withoutProject.contains("No project is selected"))
    }

    @Test
    fun build_prepends_preamble_to_first_user_turn_only() {
        val prompt = AgentPromptBuilder.build(
            tools,
            AgentContext("w", "p"),
            listOf(userMsg("first"), assistantMsg("reply"), userMsg("second")),
        )
        // Preamble mentions tools exactly once (only on the first user turn).
        assertTrue(prompt.contains("TOOLS:"))
        assertFalse(prompt.substringAfter("TOOLS:").contains("TOOLS:"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun build_renders_scratchpad_as_model_then_observation() {
        val prompt = AgentPromptBuilder.build(
            tools,
            AgentContext("w", "p"),
            listOf(userMsg("show board")),
            scratchpad = listOf(
                AgentPromptBuilder.Step(
                    rawAction = """{"tool":"list_tickets","arguments":{}}""",
                    observation = "2 tickets",
                ),
            ),
        )
        assertTrue(prompt.contains("""{"tool":"list_tickets","arguments":{}}"""))
        assertTrue(prompt.contains("Observation: 2 tickets"))
    }

    @Test
    fun build_requires_last_message_to_be_user() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentPromptBuilder.build(
                tools,
                AgentContext("w", "p"),
                listOf(userMsg("hi"), assistantMsg("done")),
            )
        }
    }
}
