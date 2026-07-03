package com.mesha.mobile.domain.ai.agent

/**
 * The workspace/project the on-device ticket agent is currently operating in.
 *
 * Tools need a project to read/write tickets and a workspace to resolve labels, statuses
 * and assignable members. The active selection comes from
 * [com.mesha.mobile.data.repository.SelectionStore]; either id can be `null` when the user
 * hasn't picked one yet, in which case tools that require it return a clear message asking
 * the user to select one rather than failing silently.
 */
data class AgentContext(
    val workspaceId: String?,
    val projectId: String?,
) {
    val hasProject: Boolean get() = !projectId.isNullOrBlank()
    val hasWorkspace: Boolean get() = !workspaceId.isNullOrBlank()
}
