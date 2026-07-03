package com.mesha.mobile.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.remote.dto.AutomationActionConditionRequestDto
import com.mesha.mobile.data.remote.dto.AutomationActionRequestDto
import com.mesha.mobile.data.remote.dto.AutomationRuleDto
import com.mesha.mobile.data.remote.dto.CreateAutomationRuleRequestDto
import com.mesha.mobile.data.remote.dto.CreateTicketRuleRequestDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.ProjectDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.TicketRuleConditionRequestDto
import com.mesha.mobile.data.remote.dto.TicketRuleDto
import com.mesha.mobile.data.remote.dto.TicketRuleRestrictionRequestDto
import com.mesha.mobile.data.remote.dto.UpdateAutomationRuleRequestDto
import com.mesha.mobile.data.remote.dto.UpdateTicketRuleRequestDto
import com.mesha.mobile.data.repository.MeshaRepository
import com.mesha.mobile.data.repository.SelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Enum value sets (source of truth is the backend; no metadata endpoint) ---
val AUTOMATION_TRIGGERS = listOf(
    "PR_OPENED", "PR_MERGED", "PR_CLOSED",
    "BLOCKS_SESSION_STARTED", "BLOCKS_SESSION_COMPLETED", "BLOCKS_SESSION_FAILED",
    "STATUS_UPDATED", "LABEL_ADDED", "AI_TOKEN_LIMIT_HIT",
)
val AUTOMATION_ACTIONS = listOf("SET_STATUS", "ADD_LABEL", "START_AI_SESSION")
val TICKET_CONDITIONS = listOf("HAS_STATUS", "HAS_LABEL", "ASSIGNED_TO_AGENT", "ASSIGNED_TO_HUMAN")
val TICKET_RESTRICTIONS = listOf("CANNOT_START_AI_SESSION", "CANNOT_MOVE_TO_STATUS")

enum class RulesTab { AUTOMATIONS, TICKET_RULES }

/** An in-progress action inside an automation being created. */
data class ActionDraft(val actionType: String = "SET_STATUS", val actionValue: String? = null)

data class AutomationForm(
    val triggerType: String = "PR_OPENED",
    val triggerValue: String? = null,
    val actions: List<ActionDraft> = listOf(ActionDraft()),
)

data class ConditionDraft(val conditionType: String = "HAS_STATUS", val conditionValue: String? = null)
data class RestrictionDraft(val restrictionType: String = "CANNOT_START_AI_SESSION", val restrictionValue: String? = null)

data class TicketRuleForm(
    val name: String = "",
    val conditions: List<ConditionDraft> = listOf(ConditionDraft()),
    val restrictions: List<RestrictionDraft> = listOf(RestrictionDraft()),
)

data class RulesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProjectId: String? = null,
    val tab: RulesTab = RulesTab.AUTOMATIONS,
    val automations: List<AutomationRuleDto> = emptyList(),
    val ticketRules: List<TicketRuleDto> = emptyList(),
    // Option sources for the value pickers
    val statuses: List<ProjectStatusDto> = emptyList(),
    val labels: List<LabelDto> = emptyList(),
    // Editors
    val automationForm: AutomationForm? = null,
    val ticketRuleForm: TicketRuleForm? = null,
    val saving: Boolean = false,
    val formError: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val meshaRepository: MeshaRepository,
    private val selectionStore: SelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = _state.asStateFlow()

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
            // Labels are workspace-scoped, so load them once alongside the project list.
            val labels = async { meshaRepository.getLabels(wsId).getOrNull().orEmpty() }
            meshaRepository.getProjects(wsId).fold(
                onSuccess = { projects ->
                    val selected = selectionStore.projectId.value
                        ?.takeIf { id -> projects.any { it.id == id } }
                        ?: projects.firstOrNull()?.id
                    _state.update {
                        it.copy(projects = projects, selectedProjectId = selected, labels = labels.await())
                    }
                    if (selected != null) loadRules(selected)
                    else _state.update { it.copy(loading = false) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun selectProject(projectId: String) {
        selectionStore.selectProject(projectId)
        _state.update { it.copy(selectedProjectId = projectId) }
        loadRules(projectId)
    }

    fun selectTab(tab: RulesTab) = _state.update { it.copy(tab = tab) }

    private fun loadRules(projectId: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val automations = async { meshaRepository.getAutomationRules(projectId).getOrNull().orEmpty() }
            val ticketRules = async { meshaRepository.getTicketRules(projectId).getOrNull().orEmpty() }
            val statuses = async {
                meshaRepository.getProjectStatuses(projectId).getOrNull().orEmpty().sortedBy { it.position ?: 0 }
            }
            _state.update {
                it.copy(
                    loading = false,
                    automations = automations.await(),
                    ticketRules = ticketRules.await(),
                    statuses = statuses.await(),
                )
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    // --- Automation editor ---

    fun startCreateAutomation() = _state.update { it.copy(automationForm = AutomationForm(), formError = null) }
    fun dismissAutomationEditor() = _state.update { it.copy(automationForm = null, formError = null, saving = false) }
    fun updateAutomationForm(transform: (AutomationForm) -> AutomationForm) =
        _state.update { it.copy(automationForm = it.automationForm?.let(transform)) }

    fun saveAutomation() {
        val form = _state.value.automationForm ?: return
        val projectId = _state.value.selectedProjectId ?: return
        if (form.actions.isEmpty()) return failForm("Add at least one action")
        val triggerValue = when (form.triggerType) {
            "STATUS_UPDATED", "LABEL_ADDED" ->
                form.triggerValue?.takeIf { it.isNotBlank() }
                    ?: return failForm("This trigger needs a value")
            else -> null
        }
        val actions = form.actions.map { a ->
            val value = when (a.actionType) {
                "SET_STATUS", "ADD_LABEL" ->
                    a.actionValue?.takeIf { it.isNotBlank() }
                        ?: return failForm("Action ${a.actionType} needs a value")
                else -> null
            }
            AutomationActionRequestDto(
                actionType = a.actionType,
                actionValue = value,
                conditions = emptyList<AutomationActionConditionRequestDto>(),
            )
        }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            meshaRepository.createAutomationRule(
                projectId,
                CreateAutomationRuleRequestDto(
                    triggerType = form.triggerType,
                    triggerValue = triggerValue,
                    actions = actions,
                ),
            ).fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, automationForm = null) }
                    loadRules(projectId)
                },
                onFailure = { e -> _state.update { it.copy(saving = false, formError = e.message) } },
            )
        }
    }

    fun toggleAutomation(rule: AutomationRuleDto) {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            meshaRepository.updateAutomationRule(
                projectId,
                rule.id,
                UpdateAutomationRuleRequestDto(enabled = !rule.enabled),
            ).fold(
                onSuccess = { updated ->
                    _state.update { s ->
                        s.copy(automations = s.automations.map { if (it.id == updated.id) updated else it })
                    }
                },
                onFailure = { e -> _state.update { it.copy(actionError = e.message) } },
            )
        }
    }

    fun deleteAutomation(ruleId: String) {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            meshaRepository.deleteAutomationRule(projectId, ruleId).fold(
                onSuccess = { _state.update { s -> s.copy(automations = s.automations.filterNot { it.id == ruleId }) } },
                onFailure = { e -> _state.update { it.copy(actionError = e.message) } },
            )
        }
    }

    // --- Ticket rule editor ---

    fun startCreateTicketRule() = _state.update { it.copy(ticketRuleForm = TicketRuleForm(), formError = null) }
    fun dismissTicketRuleEditor() = _state.update { it.copy(ticketRuleForm = null, formError = null, saving = false) }
    fun updateTicketRuleForm(transform: (TicketRuleForm) -> TicketRuleForm) =
        _state.update { it.copy(ticketRuleForm = it.ticketRuleForm?.let(transform)) }

    fun saveTicketRule() {
        val form = _state.value.ticketRuleForm ?: return
        val projectId = _state.value.selectedProjectId ?: return
        val name = form.name.trim()
        if (name.isBlank()) return failForm("Name is required")
        if (form.conditions.isEmpty()) return failForm("Add at least one condition")
        if (form.restrictions.isEmpty()) return failForm("Add at least one restriction")
        val conditions = form.conditions.map { c ->
            val value = if (c.conditionType == "HAS_STATUS" || c.conditionType == "HAS_LABEL") {
                c.conditionValue?.takeIf { it.isNotBlank() }
                    ?: return failForm("Condition ${c.conditionType} needs a value")
            } else null
            TicketRuleConditionRequestDto(conditionType = c.conditionType, conditionValue = value)
        }
        val restrictions = form.restrictions.map { r ->
            val value = if (r.restrictionType == "CANNOT_MOVE_TO_STATUS") {
                r.restrictionValue?.takeIf { it.isNotBlank() }
                    ?: return failForm("Restriction ${r.restrictionType} needs a status")
            } else null
            TicketRuleRestrictionRequestDto(restrictionType = r.restrictionType, restrictionValue = value)
        }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            meshaRepository.createTicketRule(
                projectId,
                CreateTicketRuleRequestDto(name = name, conditions = conditions, restrictions = restrictions),
            ).fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, ticketRuleForm = null) }
                    loadRules(projectId)
                },
                onFailure = { e -> _state.update { it.copy(saving = false, formError = e.message) } },
            )
        }
    }

    fun toggleTicketRule(rule: TicketRuleDto) {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            meshaRepository.updateTicketRule(
                projectId,
                rule.id,
                UpdateTicketRuleRequestDto(enabled = !rule.enabled),
            ).fold(
                onSuccess = { updated ->
                    _state.update { s -> s.copy(ticketRules = s.ticketRules.map { if (it.id == updated.id) updated else it }) }
                },
                onFailure = { e -> _state.update { it.copy(actionError = e.message) } },
            )
        }
    }

    fun deleteTicketRule(ruleId: String) {
        val projectId = _state.value.selectedProjectId ?: return
        viewModelScope.launch {
            meshaRepository.deleteTicketRule(projectId, ruleId).fold(
                onSuccess = { _state.update { s -> s.copy(ticketRules = s.ticketRules.filterNot { it.id == ruleId }) } },
                onFailure = { e -> _state.update { it.copy(actionError = e.message) } },
            )
        }
    }

    private fun failForm(message: String) {
        _state.update { it.copy(formError = message, saving = false) }
    }
}
