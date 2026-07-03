package com.mesha.mobile.domain.ai.agent

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentActionParserTest {

    private fun arg(action: AgentAction.ToolCall, key: String): String? =
        (action.arguments[key] as? JsonPrimitive)?.contentOrNull

    @Test
    fun parses_standard_tool_call_with_arguments_object() {
        val action = AgentActionParser.parse(
            """{"tool":"add_comment","arguments":{"ticket":"MES-1","body":"done"}}""",
        )
        action as AgentAction.ToolCall
        assertEquals("add_comment", action.tool)
        assertEquals("MES-1", arg(action, "ticket"))
        assertEquals("done", arg(action, "body"))
    }

    @Test
    fun parses_action_and_args_aliases() {
        val action = AgentActionParser.parse(
            """{"action":"update_ticket","args":{"ticket":"MES-2","status":"Done"}}""",
        )
        action as AgentAction.ToolCall
        assertEquals("update_ticket", action.tool)
        assertEquals("Done", arg(action, "status"))
    }

    @Test
    fun parses_flat_tool_call_where_siblings_are_arguments() {
        val action = AgentActionParser.parse(
            """{"tool":"add_comment","ticket":"MES-3","body":"looks good"}""",
        )
        action as AgentAction.ToolCall
        assertEquals("add_comment", action.tool)
        assertEquals("MES-3", arg(action, "ticket"))
        assertEquals("looks good", arg(action, "body"))
    }

    @Test
    fun lowercases_tool_name() {
        val action = AgentActionParser.parse("""{"tool":"List_Tickets","arguments":{}}""")
        action as AgentAction.ToolCall
        assertEquals("list_tickets", action.tool)
    }

    @Test
    fun parses_final_answer_field() {
        val action = AgentActionParser.parse("""{"final":"You have 3 open tickets."}""")
        action as AgentAction.Final
        assertEquals("You have 3 open tickets.", action.text)
    }

    @Test
    fun treats_final_tool_name_as_final_answer() {
        val action = AgentActionParser.parse("""{"tool":"final","answer":"All set!"}""")
        action as AgentAction.Final
        assertEquals("All set!", action.text)
    }

    @Test
    fun plain_prose_without_json_is_a_final_answer() {
        val action = AgentActionParser.parse("Sure, here's a summary of your board.")
        action as AgentAction.Final
        assertEquals("Sure, here's a summary of your board.", action.text)
    }

    @Test
    fun ignores_markdown_fences_and_surrounding_prose() {
        val raw = """
            Sure, let me look that up.
            ```json
            {"tool":"get_ticket","arguments":{"ticket":"MES-9"}}
            ```
        """.trimIndent()
        val action = AgentActionParser.parse(raw)
        action as AgentAction.ToolCall
        assertEquals("get_ticket", action.tool)
        assertEquals("MES-9", arg(action, "ticket"))
    }

    @Test
    fun prefers_the_json_block_that_looks_like_an_action() {
        val raw = """
            {"note":"thinking"}
            {"tool":"list_labels","arguments":{}}
        """.trimIndent()
        val action = AgentActionParser.parse(raw)
        action as AgentAction.ToolCall
        assertEquals("list_labels", action.tool)
    }

    @Test
    fun flat_args_exclude_scaffolding_keys() {
        val action = AgentActionParser.parse(
            """{"tool":"create_ticket","thought":"make it","title":"Fix login"}""",
        )
        action as AgentAction.ToolCall
        assertEquals("Fix login", arg(action, "title"))
        assertNull(action.arguments["thought"])
        assertNull(action.arguments["tool"])
    }

    @Test
    fun blank_input_is_empty_final() {
        val action = AgentActionParser.parse("   ")
        action as AgentAction.Final
        assertTrue(action.text.isBlank())
    }
}
