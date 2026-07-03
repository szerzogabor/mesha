package com.mesha.mobile.domain.ai.agent

import kotlinx.serialization.json.JsonObject

/**
 * The decision the model makes on a single turn of the agent loop, parsed out of its raw text
 * output by [AgentActionParser].
 *
 * Each turn the model either calls one tool ([ToolCall]) or answers the user ([Final]). The
 * loop in [TicketAgent] executes tool calls and feeds the observation back until a [Final] is
 * produced or the step budget is exhausted.
 */
sealed interface AgentAction {

    /** Invoke [tool] with [arguments]; the loop runs it and appends the observation. */
    data class ToolCall(val tool: String, val arguments: JsonObject) : AgentAction

    /** The agent's user-facing reply. Ends the loop. */
    data class Final(val text: String) : AgentAction
}
