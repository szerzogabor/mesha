package com.mesha.mobile.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.data.remote.dto.AutomationRuleDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.data.remote.dto.TicketRuleDto
import com.mesha.mobile.ui.components.EmptyState
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState
import com.mesha.mobile.ui.components.MeshaCard
import com.mesha.mobile.ui.components.MeshaTopAppBar

private enum class ValueKind { NONE, STATUS, LABEL }

private fun triggerValueKind(t: String) = when (t) {
    "STATUS_UPDATED" -> ValueKind.STATUS
    "LABEL_ADDED" -> ValueKind.LABEL
    else -> ValueKind.NONE
}

private fun actionValueKind(a: String) = when (a) {
    "SET_STATUS" -> ValueKind.STATUS
    "ADD_LABEL" -> ValueKind.LABEL
    else -> ValueKind.NONE
}

private fun conditionValueKind(c: String) = when (c) {
    "HAS_STATUS" -> ValueKind.STATUS
    "HAS_LABEL" -> ValueKind.LABEL
    else -> ValueKind.NONE
}

private fun restrictionValueKind(r: String) = when (r) {
    "CANNOT_MOVE_TO_STATUS" -> ValueKind.STATUS
    else -> ValueKind.NONE
}

private fun humanize(value: String): String =
    value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.automationForm != null -> AutomationEditor(state, viewModel)
        state.ticketRuleForm != null -> TicketRuleEditor(state, viewModel)
        else -> RulesList(state, viewModel, onBack)
    }

    state.actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            confirmButton = { TextButton(onClick = viewModel::clearActionError) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(message) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesList(
    state: RulesUiState,
    viewModel: RulesViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MeshaTopAppBar(
                title = "Project rules",
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        floatingActionButton = {
            if (state.selectedProjectId != null && !state.loading) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (state.tab == RulesTab.AUTOMATIONS) viewModel.startCreateAutomation()
                        else viewModel.startCreateTicketRule()
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (state.tab == RulesTab.AUTOMATIONS) "New automation" else "New rule") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.projects.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.projects.forEach { project ->
                        FilterChip(
                            selected = project.id == state.selectedProjectId,
                            onClick = { viewModel.selectProject(project.id) },
                            label = { Text(project.key ?: project.name) },
                        )
                    }
                }
            }

            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == RulesTab.AUTOMATIONS,
                    onClick = { viewModel.selectTab(RulesTab.AUTOMATIONS) },
                    text = { Text("Automations") },
                )
                Tab(
                    selected = state.tab == RulesTab.TICKET_RULES,
                    onClick = { viewModel.selectTab(RulesTab.TICKET_RULES) },
                    text = { Text("Ticket rules") },
                )
            }

            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
                state.tab == RulesTab.AUTOMATIONS -> AutomationsList(state, viewModel)
                else -> TicketRulesList(state, viewModel)
            }
        }
    }
}

@Composable
private fun AutomationsList(state: RulesUiState, viewModel: RulesViewModel) {
    if (state.automations.isEmpty()) {
        EmptyState("No automations. Tap New automation to add a trigger → action rule.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.automations, key = { it.id }) { rule ->
            MeshaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "When ${humanize(rule.triggerType)}" +
                                (rule.triggerValue?.let { " (${labelOrValue(it, state.labels)})" } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = rule.enabled, onCheckedChange = { viewModel.toggleAutomation(rule) })
                    }
                    rule.actions.forEach { action ->
                        Text(
                            "→ ${humanize(action.actionType)}" +
                                (action.actionValue?.let { " ${labelOrValue(it, state.labels)}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        action.conditions.forEach { cond ->
                            Text(
                                "    if ${humanize(cond.conditionType)}" +
                                    (cond.conditionValue?.let { " ${labelOrValue(it, state.labels)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.deleteAutomation(rule.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketRulesList(state: RulesUiState, viewModel: RulesViewModel) {
    if (state.ticketRules.isEmpty()) {
        EmptyState("No ticket rules. Tap New rule to restrict actions under certain conditions.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.ticketRules, key = { it.id }) { rule ->
            MeshaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            rule.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = rule.enabled, onCheckedChange = { viewModel.toggleTicketRule(rule) })
                    }
                    Text(
                        "If " + rule.conditions.joinToString(" and ") {
                            humanize(it.conditionType) + (it.conditionValue?.let { v -> " ${labelOrValue(v, state.labels)}" } ?: "")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "then " + rule.restrictions.joinToString(", ") {
                            humanize(it.restrictionType) + (it.restrictionValue?.let { v -> " ${labelOrValue(v, state.labels)}" } ?: "")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { viewModel.deleteTicketRule(rule.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun labelOrValue(value: String, labels: List<LabelDto>): String =
    labels.firstOrNull { it.id == value }?.name ?: value

// --- Automation editor ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationEditor(state: RulesUiState, viewModel: RulesViewModel) {
    val form = state.automationForm ?: return
    EditorScaffold(
        title = "New automation",
        onCancel = viewModel::dismissAutomationEditor,
    ) {
        Text("Trigger", style = MaterialTheme.typography.labelLarge)
        EnumDropdown(
            label = "When",
            selected = form.triggerType,
            options = AUTOMATION_TRIGGERS,
            onSelect = { t -> viewModel.updateAutomationForm { it.copy(triggerType = t, triggerValue = null) } },
        )
        ValuePicker(
            kind = triggerValueKind(form.triggerType),
            value = form.triggerValue,
            statuses = state.statuses,
            labels = state.labels,
            onSelect = { v -> viewModel.updateAutomationForm { it.copy(triggerValue = v) } },
        )

        HorizontalDivider()
        Text("Actions", style = MaterialTheme.typography.labelLarge)
        form.actions.forEachIndexed { index, action ->
            MeshaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Action ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (form.actions.size > 1) {
                            IconButton(onClick = {
                                viewModel.updateAutomationForm {
                                    it.copy(actions = it.actions.filterIndexed { i, _ -> i != index })
                                }
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove action") }
                        }
                    }
                    EnumDropdown(
                        label = "Do",
                        selected = action.actionType,
                        options = AUTOMATION_ACTIONS,
                        onSelect = { t ->
                            viewModel.updateAutomationForm {
                                it.copy(actions = it.actions.mapIndexed { i, a ->
                                    if (i == index) a.copy(actionType = t, actionValue = null) else a
                                })
                            }
                        },
                    )
                    ValuePicker(
                        kind = actionValueKind(action.actionType),
                        value = action.actionValue,
                        statuses = state.statuses,
                        labels = state.labels,
                        onSelect = { v ->
                            viewModel.updateAutomationForm {
                                it.copy(actions = it.actions.mapIndexed { i, a ->
                                    if (i == index) a.copy(actionValue = v) else a
                                })
                            }
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = {
            viewModel.updateAutomationForm { it.copy(actions = it.actions + ActionDraft()) }
        }) { Text("Add action") }

        state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::saveAutomation, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Saving…" else "Save automation")
        }
    }
}

// --- Ticket rule editor ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketRuleEditor(state: RulesUiState, viewModel: RulesViewModel) {
    val form = state.ticketRuleForm ?: return
    EditorScaffold(
        title = "New ticket rule",
        onCancel = viewModel::dismissTicketRuleEditor,
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.updateTicketRuleForm { it.copy(name = v) } },
            label = { Text("Rule name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text("Conditions (if all match)", style = MaterialTheme.typography.labelLarge)
        form.conditions.forEachIndexed { index, cond ->
            MeshaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Condition ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (form.conditions.size > 1) {
                            IconButton(onClick = {
                                viewModel.updateTicketRuleForm {
                                    it.copy(conditions = it.conditions.filterIndexed { i, _ -> i != index })
                                }
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove condition") }
                        }
                    }
                    EnumDropdown(
                        label = "If",
                        selected = cond.conditionType,
                        options = TICKET_CONDITIONS,
                        onSelect = { t ->
                            viewModel.updateTicketRuleForm {
                                it.copy(conditions = it.conditions.mapIndexed { i, c ->
                                    if (i == index) c.copy(conditionType = t, conditionValue = null) else c
                                })
                            }
                        },
                    )
                    ValuePicker(
                        kind = conditionValueKind(cond.conditionType),
                        value = cond.conditionValue,
                        statuses = state.statuses,
                        labels = state.labels,
                        onSelect = { v ->
                            viewModel.updateTicketRuleForm {
                                it.copy(conditions = it.conditions.mapIndexed { i, c ->
                                    if (i == index) c.copy(conditionValue = v) else c
                                })
                            }
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = {
            viewModel.updateTicketRuleForm { it.copy(conditions = it.conditions + ConditionDraft()) }
        }) { Text("Add condition") }

        HorizontalDivider()
        Text("Restrictions (then block)", style = MaterialTheme.typography.labelLarge)
        form.restrictions.forEachIndexed { index, restriction ->
            MeshaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Restriction ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (form.restrictions.size > 1) {
                            IconButton(onClick = {
                                viewModel.updateTicketRuleForm {
                                    it.copy(restrictions = it.restrictions.filterIndexed { i, _ -> i != index })
                                }
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove restriction") }
                        }
                    }
                    EnumDropdown(
                        label = "Block",
                        selected = restriction.restrictionType,
                        options = TICKET_RESTRICTIONS,
                        onSelect = { t ->
                            viewModel.updateTicketRuleForm {
                                it.copy(restrictions = it.restrictions.mapIndexed { i, r ->
                                    if (i == index) r.copy(restrictionType = t, restrictionValue = null) else r
                                })
                            }
                        },
                    )
                    ValuePicker(
                        kind = restrictionValueKind(restriction.restrictionType),
                        value = restriction.restrictionValue,
                        statuses = state.statuses,
                        labels = state.labels,
                        onSelect = { v ->
                            viewModel.updateTicketRuleForm {
                                it.copy(restrictions = it.restrictions.mapIndexed { i, r ->
                                    if (i == index) r.copy(restrictionValue = v) else r
                                })
                            }
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = {
            viewModel.updateTicketRuleForm { it.copy(restrictions = it.restrictions + RestrictionDraft()) }
        }) { Text("Add restriction") }

        state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::saveTicketRule, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Saving…" else "Save rule")
        }
    }
}

// --- Shared editor pieces ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(
    title: String,
    onCancel: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            MeshaTopAppBar(
                title = title,
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun EnumDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${humanize(selected)}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(humanize(option)) },
                    onClick = { expanded = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun ValuePicker(
    kind: ValueKind,
    value: String?,
    statuses: List<ProjectStatusDto>,
    labels: List<LabelDto>,
    onSelect: (String) -> Unit,
) {
    when (kind) {
        ValueKind.NONE -> Unit
        ValueKind.STATUS -> ValueDropdown(
            label = "Status",
            selectedDisplay = value ?: "Choose…",
            options = statuses.map { it.name to it.name },
            onSelect = onSelect,
        )
        ValueKind.LABEL -> ValueDropdown(
            label = "Label",
            selectedDisplay = labels.firstOrNull { it.id == value }?.name ?: "Choose…",
            options = labels.map { it.name to it.id },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun ValueDropdown(
    label: String,
    selectedDisplay: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selectedDisplay") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("None available") }, onClick = { expanded = false })
            }
            options.forEach { (display, v) ->
                DropdownMenuItem(text = { Text(display) }, onClick = { expanded = false; onSelect(v) })
            }
        }
    }
}
