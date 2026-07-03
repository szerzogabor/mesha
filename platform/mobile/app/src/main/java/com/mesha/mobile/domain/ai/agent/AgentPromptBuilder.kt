package com.mesha.mobile.domain.ai.agent

import com.mesha.mobile.domain.ai.LocalChatMessage

/**
 * Builds the single prompt string for one turn of the on-device ticket agent, in the Gemma
 * instruction-tuned turn format.
 *
 * The prompt has three parts, all folded into the Gemma `<start_of_turn>` transcript because
 * the on-device engines have no dedicated system role:
 *  1. a preamble (role, the ReAct-style JSON protocol, the tool catalog, active-project context)
 *     prepended to the first user turn;
 *  2. the finished conversation so far (prior user questions and the agent's prior final answers);
 *  3. the current turn's scratchpad — each tool call the model already made this turn followed by
 *     its `Observation:` — ending with an open model turn for the model to continue from.
 *
 * Pure and dependency-free so it can be unit-tested without an Android runtime.
 */
object AgentPromptBuilder {

    /** One executed tool step within the current turn: the model's raw action and its observation. */
    data class Step(val rawAction: String, val observation: String)

    private const val USER_START = "<start_of_turn>user\n"
    private const val MODEL_START = "<start_of_turn>model\n"
    private const val TURN_END = "<end_of_turn>\n"

    /**
     * @param tools the catalog exposed to the model this turn
     * @param context the active workspace/project selection
     * @param history the conversation; the last entry MUST be the current USER message
     * @param scratchpad tool calls already executed on the current turn (empty on the first pass)
     */
    fun build(
        tools: List<AgentTool>,
        context: AgentContext,
        history: List<LocalChatMessage>,
        scratchpad: List<Step> = emptyList(),
    ): String {
        require(history.isNotEmpty()) { "Conversation history must not be empty" }
        require(history.last().role == LocalChatMessage.Role.USER) {
            "Last message in history must be a USER message"
        }

        val firstUserIndex = history.indexOfFirst { it.role == LocalChatMessage.Role.USER }

        return buildString {
            history.forEachIndexed { index, message ->
                when (message.role) {
                    LocalChatMessage.Role.USER -> {
                        append(USER_START)
                        if (index == firstUserIndex) {
                            append(preamble(tools, context))
                            append("\n\n")
                        }
                        append(message.content.trim())
                        append("\n")
                        append(TURN_END)
                    }
                    LocalChatMessage.Role.ASSISTANT -> {
                        append(MODEL_START)
                        append(message.content.trim())
                        append("\n")
                        append(TURN_END)
                    }
                }
            }

            for (step in scratchpad) {
                append(MODEL_START)
                append(step.rawAction.trim())
                append("\n")
                append(TURN_END)
                append(USER_START)
                append("Observation: ")
                append(step.observation.trim())
                append("\n")
                append(TURN_END)
            }

            // Open a model turn so the model continues with its next action.
            append(MODEL_START)
        }
    }

    internal fun preamble(tools: List<AgentTool>, context: AgentContext): String = buildString {
        appendLine("You are Mesha's on-device project assistant. You help the user read and manage")
        appendLine("their project tickets by calling tools. Work step by step.")
        appendLine()
        appendLine("PROTOCOL — every reply is EXACTLY ONE JSON object and nothing else. Either:")
        appendLine("""  1. Call a tool:   {"tool":"<name>","arguments":{ ... }}""")
        appendLine("""  2. Answer the user: {"final":"<your reply in plain text>"}""")
        appendLine("Never output both, never add prose outside the JSON.")
        appendLine("After a tool call you receive a line starting with 'Observation:' — read it,")
        appendLine("then either call another tool or give the final answer. Prefer reading a ticket")
        appendLine("before changing it, and keep final answers short and friendly.")
        appendLine()
        appendLine(
            if (context.hasProject) {
                "A project is currently selected, so ticket tools are available."
            } else {
                "No project is selected. Ticket tools will report that — ask the user to open a " +
                    "project first if they want ticket actions."
            }
        )
        appendLine()
        appendLine("TOOLS:")
        for (tool in tools) {
            appendLine("- ${tool.name}: ${tool.description}")
            appendLine("    arguments: ${tool.argsSpec}")
        }
        appendLine()
        appendLine("EXAMPLES:")
        appendLine("""User: what's on the board?  ->  {"tool":"list_tickets","arguments":{}}""")
        appendLine(
            """After observation  ->  {"final":"You have 3 open tickets: MES-1 Login bug, …"}"""
        )
        appendLine(
            """User: mark MES-4 done  ->  {"tool":"update_ticket","arguments":{"ticket":"MES-4","status":"Done"}}"""
        )
    }.trimEnd()
}
