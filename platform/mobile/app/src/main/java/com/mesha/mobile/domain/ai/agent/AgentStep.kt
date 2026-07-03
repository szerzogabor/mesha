package com.mesha.mobile.domain.ai.agent

/**
 * A progress event streamed from [TicketAgent] while it works, so the chat UI can show what
 * the agent is doing between the user's message and the final reply (e.g. "🔧 update_ticket"
 * then "✓ Updated MES-42"). Purely for display — the agent's actual answer is the return value
 * of [TicketAgent.run], not any of these steps.
 */
sealed interface AgentStep {

    /** The agent decided to call a tool. [summary] is a short human-readable call, e.g. `update_ticket(MES-42)`. */
    data class ToolInvocation(val tool: String, val summary: String) : AgentStep

    /** The result of the most recent [ToolInvocation], already trimmed for display. */
    data class ToolResult(val tool: String, val observation: String) : AgentStep
}
