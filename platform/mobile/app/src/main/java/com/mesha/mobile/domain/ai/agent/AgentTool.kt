package com.mesha.mobile.domain.ai.agent

import kotlinx.serialization.json.JsonObject

/**
 * A single capability the on-device ticket agent can invoke — reading or mutating tickets,
 * comments, labels, statuses or members through the Mesha REST API.
 *
 * Tools are described to the model in the prompt ([AgentPromptBuilder]) using [name],
 * [description] and [argsSpec], and dispatched by [AgentToolRegistry] when the model emits a
 * matching [AgentAction.ToolCall]. [execute] returns a short, already-summarized observation
 * string that is fed back into the model's reasoning scratchpad, so implementations must keep
 * output compact (the on-device context window is small) and never throw for expected failures
 * — return a human-readable error string instead.
 */
interface AgentTool {

    /** Stable tool identifier the model calls by, e.g. `"update_ticket"`. snake_case. */
    val name: String

    /** One-sentence description of what the tool does, shown to the model. */
    val description: String

    /**
     * Compact JSON argument spec shown to the model, e.g.
     * `{"ticket":"identifier or title","body":"comment text"}`. Use `?` to mark optional keys.
     */
    val argsSpec: String

    /**
     * Execute the tool with the model-supplied [args] in the given [context]. Returns an
     * observation string for the agent scratchpad. Must not throw for expected error
     * conditions (missing project, unknown ticket, API failure) — return a clear message.
     */
    suspend fun execute(args: JsonObject, context: AgentContext): String
}
