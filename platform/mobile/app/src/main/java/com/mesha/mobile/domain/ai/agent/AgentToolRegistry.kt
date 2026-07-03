package com.mesha.mobile.domain.ai.agent

import com.mesha.mobile.data.repository.MeshaRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The catalog of ticket tools the on-device agent can call, all backed by [MeshaRepository].
 *
 * Read tools are listed before write tools so the prompt nudges the model to look before it
 * leaps. [find] resolves a model-supplied tool name (case-insensitively) to its implementation.
 */
@Singleton
class AgentToolRegistry @Inject constructor(
    repository: MeshaRepository,
) {
    val tools: List<AgentTool> = listOf(
        // read
        ListTicketsTool(repository),
        GetTicketTool(repository),
        ListCommentsTool(repository),
        ListLabelsTool(repository),
        ListStatusesTool(repository),
        ListMembersTool(repository),
        // write
        CreateTicketTool(repository),
        UpdateTicketTool(repository),
        AddCommentTool(repository),
    )

    private val byName: Map<String, AgentTool> = tools.associateBy { it.name.lowercase() }

    fun find(name: String): AgentTool? = byName[name.trim().lowercase()]
}
