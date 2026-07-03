package com.mesha.mobile.ui.screens.agentconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.AgentDefinitionDto
import com.mesha.mobile.data.remote.dto.CreateAgentDefinitionRequestDto
import com.mesha.mobile.data.remote.dto.UpdateAgentDefinitionRequestDto
import com.mesha.mobile.data.remote.dto.withStartupCommands
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Provider types the backend accepts for an agent definition. */
val AGENT_PROVIDER_TYPES = listOf("BLOCKS", "QWEN")

/** Backend name constraint: lowercase kebab-case. */
private val NAME_REGEX = Regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

/** Editing form for an agent definition. [editingId] null => creating a new one. */
data class AgentForm(
    val editingId: String? = null,
    val title: String = "",
    val name: String = "",
    val description: String = "",
    val providerType: String = "BLOCKS",
    val blocksAgentName: String = "",
    val systemPrompt: String = "",
    val startupCommandsText: String = "",
    val active: Boolean = true,
    // Preserved so edits don't drop provider parameters the app doesn't manage.
    val originalProviderParameters: JsonObject? = null,
)

data class AgentConfigUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val agents: List<AgentDefinitionDto> = emptyList(),
    val editing: AgentForm? = null,
    val saving: Boolean = false,
    val formError: String? = null,
    val deletingId: String? = null,
)

@HiltViewModel
class AgentConfigViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentConfigUiState())
    val state: StateFlow<AgentConfigUiState> = _state.asStateFlow()

    private var workspaceId: String? = null

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val wsId = selectionStore.workspaceId.value
                ?: meshaRepository.getWorkspaces().getOrNull()?.firstOrNull()?.id
                    ?.also { selectionStore.selectWorkspace(it) }
            if (wsId == null) {
                _state.update { it.copy(loading = false, error = "No workspace available") }
                return@launch
            }
            workspaceId = wsId
            meshaRepository.getAgentDefinitions(wsId).fold(
                onSuccess = { agents -> _state.update { it.copy(loading = false, agents = agents) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    // --- Editor ---

    fun startCreate() = _state.update { it.copy(editing = AgentForm(), formError = null) }

    fun startEdit(agent: AgentDefinitionDto) = _state.update {
        it.copy(
            formError = null,
            editing = AgentForm(
                editingId = agent.id,
                title = agent.title,
                name = agent.name,
                description = agent.description.orEmpty(),
                providerType = agent.providerType,
                blocksAgentName = agent.blocksAgentName.orEmpty(),
                systemPrompt = agent.systemPrompt.orEmpty(),
                startupCommandsText = agent.startupCommands.joinToString("\n"),
                active = agent.active,
                originalProviderParameters = agent.providerParameters,
            ),
        )
    }

    fun dismissEditor() = _state.update { it.copy(editing = null, formError = null, saving = false) }

    fun updateForm(transform: (AgentForm) -> AgentForm) = _state.update {
        it.copy(editing = it.editing?.let(transform))
    }

    fun save() {
        val form = _state.value.editing ?: return
        val wsId = workspaceId ?: return
        val title = form.title.trim()
        val name = form.name.trim()
        val systemPrompt = form.systemPrompt.trim()
        when {
            title.isBlank() -> return fail("Title is required")
            name.isBlank() -> return fail("Name is required")
            !NAME_REGEX.matches(name) ->
                return fail("Name must be lowercase kebab-case (e.g. my-agent)")
            systemPrompt.isBlank() -> return fail("System prompt is required")
        }
        val commands = form.startupCommandsText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val providerParameters = form.originalProviderParameters.withStartupCommands(commands)
        val blocksAgentName = form.blocksAgentName.trim().takeIf { it.isNotBlank() }
        val description = form.description.trim().takeIf { it.isNotBlank() }

        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val result = if (form.editingId == null) {
                meshaRepository.createAgentDefinition(
                    wsId,
                    CreateAgentDefinitionRequestDto(
                        title = title,
                        name = name,
                        description = description,
                        providerType = form.providerType,
                        systemPrompt = systemPrompt,
                        providerParameters = providerParameters,
                        blocksAgentName = blocksAgentName,
                        active = form.active,
                    ),
                )
            } else {
                meshaRepository.updateAgentDefinition(
                    wsId,
                    form.editingId,
                    UpdateAgentDefinitionRequestDto(
                        title = title,
                        name = name,
                        description = description,
                        providerType = form.providerType,
                        systemPrompt = systemPrompt,
                        providerParameters = providerParameters,
                        blocksAgentName = blocksAgentName,
                        active = form.active,
                    ),
                )
            }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, editing = null) }
                    load()
                },
                onFailure = { e -> _state.update { it.copy(saving = false, formError = e.message) } },
            )
        }
    }

    fun delete(agentId: String) {
        val wsId = workspaceId ?: return
        _state.update { it.copy(deletingId = agentId) }
        viewModelScope.launch {
            meshaRepository.deleteAgentDefinition(wsId, agentId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(deletingId = null, agents = it.agents.filterNot { a -> a.id == agentId })
                    }
                },
                onFailure = { e -> _state.update { it.copy(deletingId = null, error = e.message) } },
            )
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(formError = message, saving = false) }
    }
}
