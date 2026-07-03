package com.mesha.mobile.domain.ai.agent

import com.mesha.mobile.data.repository.SelectionStore
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.ai.LocalChatMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A ReAct-style agent that runs entirely on-device: it drives the local LLM
 * ([LocalAiProvider.generate]) in a think→act→observe loop, letting it call the ticket
 * [AgentTool]s to read and modify Mesha tickets, and returns a plain-language answer for the
 * chat.
 *
 * Because the on-device engines only do plain text generation (no native function calling), the
 * whole protocol is prompt-driven: [AgentPromptBuilder] renders the tool catalog and the JSON
 * action protocol, [AgentActionParser] extracts the model's chosen action, and this loop
 * executes tool calls and feeds observations back until the model answers or the step budget
 * ([MAX_STEPS]) runs out. The budget bounds latency and cost on weak devices and guarantees
 * termination even if the model never emits a final answer.
 */
@Singleton
class TicketAgent @Inject constructor(
    private val localAi: LocalAiProvider,
    private val registry: AgentToolRegistry,
    private val selectionStore: SelectionStore,
) {

    /**
     * Run the agent for one user turn.
     *
     * @param history the full conversation; the last entry must be the new USER message.
     * @param onStep invoked as the agent calls tools, so the UI can show live progress. Called
     *   on the same coroutine as [run]; keep handlers cheap.
     * @return the agent's user-facing reply.
     * @throws com.mesha.mobile.domain.ai.LocalAiException if the model is unavailable or fails.
     */
    suspend fun run(
        history: List<LocalChatMessage>,
        onStep: (AgentStep) -> Unit = {},
    ): String {
        val context = AgentContext(
            workspaceId = selectionStore.workspaceId.value,
            projectId = selectionStore.projectId.value,
        )
        val scratchpad = mutableListOf<AgentPromptBuilder.Step>()
        var lastObservation: String? = null

        repeat(MAX_STEPS) {
            val prompt = AgentPromptBuilder.build(registry.tools, context, history, scratchpad)
            val raw = localAi.generate(prompt)

            when (val action = AgentActionParser.parse(raw)) {
                is AgentAction.Final -> {
                    val text = action.text.trim()
                    return text.ifBlank {
                        lastObservation ?: "Sorry, I couldn't come up with a response."
                    }
                }

                is AgentAction.ToolCall -> {
                    val tool = registry.find(action.tool)
                    onStep(AgentStep.ToolInvocation(action.tool, summarize(action)))

                    val observation = if (tool == null) {
                        "Unknown tool \"${action.tool}\". Available tools: " +
                            registry.tools.joinToString(", ") { it.name } +
                            """. Call one of these, or answer with {"final":"…"}."""
                    } else {
                        runCatching { tool.execute(action.arguments, context) }
                            .getOrElse { "The tool failed: ${it.message ?: "unknown error"}." }
                    }

                    lastObservation = observation
                    onStep(AgentStep.ToolResult(action.tool, observation))
                    scratchpad.add(
                        AgentPromptBuilder.Step(rawAction = canonicalJson(action), observation = observation),
                    )
                }
            }
        }

        // Step budget exhausted without a final answer — return the most useful thing we have.
        return lastObservation?.let { "Here's what I found: $it" }
            ?: "I couldn't complete that request. Please try rephrasing."
    }

    /** A short human-readable rendering of a tool call for the progress UI, e.g. `update_ticket · MES-4`. */
    private fun summarize(action: AgentAction.ToolCall): String {
        val hint = listOf("ticket", "title", "status", "search", "query")
            .firstNotNullOfOrNull { key ->
                (action.arguments[key] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
            }
        return if (hint != null) "${action.tool} · $hint" else action.tool
    }

    /** Canonical `{"tool":…,"arguments":{…}}` echoed back into the scratchpad transcript. */
    private fun canonicalJson(action: AgentAction.ToolCall): String =
        JsonObject(
            mapOf(
                "tool" to JsonPrimitive(action.tool),
                "arguments" to action.arguments,
            ),
        ).toString()

    private companion object {
        const val MAX_STEPS = 6
    }
}
