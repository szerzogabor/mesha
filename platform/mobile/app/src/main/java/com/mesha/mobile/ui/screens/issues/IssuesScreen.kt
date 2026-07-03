package com.mesha.mobile.ui.screens.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.ui.components.EmptyState
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState
import com.mesha.mobile.ui.components.parseHexColor

private val PRIORITIES = listOf("URGENT", "HIGH", "MEDIUM", "LOW")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(
    onCreateIssueWithAi: () -> Unit,
    onCreateIssueManual: () -> Unit,
    onOpenIssue: (projectId: String, issueId: String) -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Load the next page when the user scrolls near the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.issues.size - 3 && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Issues") },
                actions = {
                    IconButton(onClick = { viewModel.setViewMode(IssueViewMode.LIST) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = "List view",
                            tint = if (state.viewMode == IssueViewMode.LIST)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.setViewMode(IssueViewMode.BOARD) }) {
                        Icon(
                            Icons.Filled.ViewColumn,
                            contentDescription = "Board view",
                            tint = if (state.viewMode == IssueViewMode.BOARD)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onCreateIssueWithAi) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "Create with AI")
                }
                ExtendedFloatingActionButton(
                    onClick = onCreateIssueManual,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.projects.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.projects.take(8).forEach { project ->
                        FilterChip(
                            selected = project.id == state.selectedProjectId,
                            onClick = { viewModel.selectProject(project.id) },
                            label = { Text(project.key ?: project.name) },
                        )
                    }
                }
            }

            FilterBar(state = state, viewModel = viewModel)

            when {
                state.loading -> LoadingState()
                state.error != null && state.issues.isEmpty() ->
                    ErrorState(state.error!!, onRetry = viewModel::load)
                state.issues.isEmpty() -> EmptyState("No issues match. Tap New to create one.")
                state.viewMode == IssueViewMode.BOARD -> BoardView(
                    state = state,
                    onOpenIssue = onOpenIssue,
                    onMove = viewModel::moveIssueStatus,
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.issues, key = { it.id }) { issue ->
                        IssueRow(
                            issue = issue,
                            statuses = state.statuses,
                            onClick = { onOpenIssue(issue.projectId, issue.id) },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        // A rejected board move (e.g. a ticket rule blocking the target status) surfaces here.
        state.moveError?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearMoveError,
                confirmButton = { TextButton(onClick = viewModel::clearMoveError) { Text("OK") } },
                title = { Text("Couldn't move issue") },
                text = { Text(message) },
            )
        }
    }
}

/**
 * Board (Kanban) view: one horizontally-scrollable column per project status. Issues are
 * grouped into their status column; any status present on issues but not in the project's
 * configured statuses becomes a trailing column so nothing is hidden. Moving a card is a
 * tap action (Compose drag-and-drop across scrollable columns is unreliable on touch).
 */
@Composable
private fun BoardView(
    state: IssuesUiState,
    onOpenIssue: (projectId: String, issueId: String) -> Unit,
    onMove: (issueId: String, newStatus: String) -> Unit,
) {
    // The board can hold up to a few hundred issues, so keep these O(N) groupings out of
    // the recomposition path — recompute only when the statuses or issues actually change.
    val columns: List<Pair<String, String?>> = remember(state.statuses, state.issues) {
        val knownNames = state.statuses.map { it.name }
        val orphanNames = state.issues.mapNotNull { it.status }
            .filter { it.isNotBlank() && it !in knownNames }
            .distinct()
        state.statuses.map { it.name to it.color } + orphanNames.map { it to null }
    }
    val allStatusNames = remember(columns) { columns.map { it.first } }
    val issuesByStatus = remember(state.issues) { state.issues.groupBy { it.status.orEmpty() } }

    if (columns.isEmpty()) {
        EmptyState("No statuses configured for this project.")
        return
    }

    Row(
        Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        columns.forEach { (statusName, color) ->
            BoardColumn(
                statusName = statusName,
                color = color,
                issues = issuesByStatus[statusName].orEmpty(),
                allStatusNames = allStatusNames,
                onOpenIssue = onOpenIssue,
                onMove = onMove,
            )
        }
    }
}

@Composable
private fun BoardColumn(
    statusName: String,
    color: String?,
    issues: List<IssueDto>,
    allStatusNames: List<String>,
    onOpenIssue: (projectId: String, issueId: String) -> Unit,
    onMove: (issueId: String, newStatus: String) -> Unit,
) {
    Column(
        Modifier
            .width(288.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(color)
            Text(
                statusName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Badge { Text(issues.size.toString()) }
        }
        if (issues.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No issues",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(issues, key = { it.id }) { issue ->
                    BoardCard(
                        issue = issue,
                        moveTargets = allStatusNames.filter { it != issue.status },
                        onClick = { onOpenIssue(issue.projectId, issue.id) },
                        onMove = { target -> onMove(issue.id, target) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardCard(
    issue: IssueDto,
    moveTargets: List<String>,
    onClick: () -> Unit,
    onMove: (String) -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                issue.identifier?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                issue.priority?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                issue.lastPullRequest?.let { pr -> PrBadge(state = pr.state, checks = pr.checksStatus) }
            }
            Text(issue.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (issue.labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    issue.labels.take(3).forEach { LabelChip(it.name, it.color) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                issue.assignee?.name?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                } ?: Box(Modifier.weight(1f))
                MoveMenu(targets = moveTargets, onMove = onMove)
            }
        }
    }
}

@Composable
private fun MoveMenu(targets: List<String>, onMove: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = targets.isNotEmpty()) {
            Text("Move")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            targets.forEach { target ->
                DropdownMenuItem(
                    text = { Text(target) },
                    onClick = {
                        expanded = false
                        onMove(target)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(state: IssuesUiState, viewModel: IssuesViewModel) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search issues") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Status filter — only in list mode; the board already shows every status column.
            if (state.viewMode == IssueViewMode.LIST) {
                FilterDropdown(
                    label = state.statusFilter ?: "Status",
                    selected = state.statusFilter != null,
                    options = listOf<Pair<String, String?>>("All statuses" to null) +
                        state.statuses.map { it.name to it.name },
                    onSelect = { viewModel.setStatusFilter(it) },
                )
            }
            // Priority filter
            FilterDropdown(
                label = state.priorityFilter ?: "Priority",
                selected = state.priorityFilter != null,
                options = listOf<Pair<String, String?>>("All priorities" to null) +
                    PRIORITIES.map { it to it },
                onSelect = { viewModel.setPriorityFilter(it) },
            )
            // Sort
            SortDropdown(state = state, onSelect = viewModel::setSort)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selected: Boolean,
    options: List<Pair<String, String?>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(state: IssuesUiState, onSelect: (IssueSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(state.sort.label + if (state.sortDescending) " ↓" else " ↑") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IssueSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: IssueDto,
    statuses: List<ProjectStatusDto>,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                issue.identifier?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                issue.priority?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                issue.lastPullRequest?.let { pr ->
                    PrBadge(state = pr.state, checks = pr.checksStatus)
                }
            }
            Text(issue.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                issue.status?.let { status ->
                    val color = statuses.firstOrNull { it.name == status }?.color
                    StatusDot(color)
                    Text(status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                issue.assignee?.name?.let {
                    Text("· $it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (issue.labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    issue.labels.take(4).forEach { LabelChip(it.name, it.color) }
                }
            }
        }
    }
}

@Composable
fun StatusDot(colorHex: String?) {
    val color = parseHexColor(colorHex, MaterialTheme.colorScheme.outline)
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun LabelChip(name: String, colorHex: String?) {
    val color = parseHexColor(colorHex, MaterialTheme.colorScheme.secondary)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(name, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrBadge(state: String?, checks: String?) {
    val label = "PR" + (state?.let { " · ${it.lowercase()}" } ?: "")
    val checkColor = when (checks?.lowercase()) {
        "success" -> Color_Green
        "failure", "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (checks != null) Box(Modifier.size(8.dp).clip(CircleShape).background(checkColor))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

private val Color_Green = androidx.compose.ui.graphics.Color(0xFF2E7D32)
