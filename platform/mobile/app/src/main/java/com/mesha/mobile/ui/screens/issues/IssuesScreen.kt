package com.mesha.mobile.ui.screens.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.mesha.mobile.data.remote.dto.GitHubPullRequestDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.ProjectStatusDto
import com.mesha.mobile.ui.components.EmptyState
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState
import com.mesha.mobile.ui.components.MeshaCard
import com.mesha.mobile.ui.components.MeshaTopAppBar
import com.mesha.mobile.ui.components.formatRelativeTime
import com.mesha.mobile.ui.components.parseHexColor
import com.mesha.mobile.ui.theme.Mesha

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MeshaTopAppBar(
                title = "Issues",
                actions = {
                    ViewModeSwitcher(
                        mode = state.viewMode,
                        onSelect = viewModel::setViewMode,
                        modifier = Modifier.padding(end = 8.dp),
                    )
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

            // Only the board loads every issue at once; the list is paginated, so its
            // in-memory size would understate the real total and grow as you scroll.
            if (state.viewMode == IssueViewMode.BOARD && state.issues.isNotEmpty()) {
                Text(
                    "${state.issues.size} total",
                    style = MaterialTheme.typography.labelMedium,
                    color = Mesha.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
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
                    onCreateIssue = onCreateIssueManual,
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

/** A card being dragged across the board. Positions are in window coordinates. */
private data class BoardDrag(
    val issue: IssueDto,
    val pointer: Offset,
    val grab: Offset,
    val cardSize: IntSize,
)

/**
 * Board (Kanban) view: one horizontally-scrollable column per project status. Issues are
 * grouped into their status column; any status present on issues but not in the project's
 * configured statuses becomes a trailing column so nothing is hidden.
 *
 * A card is moved to another column by long-pressing it and dragging onto the target
 * column (the board auto-scrolls horizontally when the card nears an edge), or via the
 * card's "Move" menu as a fallback. Both routes call [onMove].
 */
@Composable
private fun BoardView(
    state: IssuesUiState,
    onOpenIssue: (projectId: String, issueId: String) -> Unit,
    onMove: (issueId: String, newStatus: String) -> Unit,
    onCreateIssue: () -> Unit,
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

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // Window-space bounds of each status column, kept current as the board scrolls, so a
    // dragged card can be matched to whichever column sits under the finger.
    val columnBounds = remember { mutableStateMapOf<String, Rect>() }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var drag by remember { mutableStateOf<BoardDrag?>(null) }

    // The column currently under the drag point — null when over nothing or over the card's
    // own column. Recomputes as the finger moves AND as columns scroll beneath a held card.
    val dropTarget by remember {
        derivedStateOf {
            val d = drag ?: return@derivedStateOf null
            columnBounds.entries
                .firstOrNull { (_, r) -> d.pointer.x >= r.left && d.pointer.x <= r.right }
                ?.key
                ?.takeIf { it != d.issue.status }
        }
    }

    // Auto-scroll the board horizontally while a dragged card hovers near either edge, so
    // off-screen columns on a narrow phone remain reachable.
    val autoScroll by remember {
        derivedStateOf {
            val d = drag ?: return@derivedStateOf 0f
            val edge = with(density) { 56.dp.toPx() }
            val left = boardOrigin.x
            val right = boardOrigin.x + boardSize.width
            when {
                d.pointer.x > right - edge -> 1f
                d.pointer.x < left + edge -> -1f
                else -> 0f
            }
        }
    }
    LaunchedEffect(autoScroll) {
        if (autoScroll != 0f) {
            val step = with(density) { 12.dp.toPx() }
            while (isActive) {
                scrollState.scrollBy(autoScroll * step)
                delay(16)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                boardOrigin = it.positionInWindow()
                boardSize = it.size
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            columns.forEach { (statusName, color) ->
                BoardColumn(
                    statusName = statusName,
                    color = color,
                    issues = issuesByStatus[statusName].orEmpty(),
                    allStatusNames = allStatusNames,
                    isDropTarget = dropTarget == statusName,
                    draggingIssueId = drag?.issue?.id,
                    onBoundsChanged = { rect -> columnBounds[statusName] = rect },
                    onOpenIssue = onOpenIssue,
                    onMove = onMove,
                    onCreateIssue = onCreateIssue,
                    onDragStart = { issue, cardOrigin, cardSize, grab ->
                        drag = BoardDrag(issue, cardOrigin + grab, grab, cardSize)
                    },
                    onDragMove = { pointer -> drag = drag?.copy(pointer = pointer) },
                    onDragEnd = {
                        val d = drag
                        val target = dropTarget
                        if (d != null && target != null) onMove(d.issue.id, target)
                        drag = null
                    },
                    onDragCancel = { drag = null },
                )
            }
        }

        // Floating copy of the card that tracks the finger during a drag.
        drag?.let { d ->
            DraggingCardOverlay(
                issue = d.issue,
                widthPx = d.cardSize.width,
                density = density,
                offset = {
                    IntOffset(
                        (d.pointer.x - boardOrigin.x - d.grab.x).roundToInt(),
                        (d.pointer.y - boardOrigin.y - d.grab.y).roundToInt(),
                    )
                },
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
    isDropTarget: Boolean,
    draggingIssueId: String?,
    onBoundsChanged: (Rect) -> Unit,
    onOpenIssue: (projectId: String, issueId: String) -> Unit,
    onMove: (issueId: String, newStatus: String) -> Unit,
    onCreateIssue: () -> Unit,
    onDragStart: (issue: IssueDto, cardOrigin: Offset, cardSize: IntSize, grab: Offset) -> Unit,
    onDragMove: (pointer: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val accent = parseHexColor(color, Mesha.colors.accent)
    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Column header: drag handle · status dot · UPPERCASE name · count pill
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = null,
                tint = Mesha.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(
                statusName.replace('_', ' ').uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Mesha.colors.textPrimary,
                letterSpacing = 0.5.sp,
            )
            CountPill(count = issues.size, color = accent)
        }

        // Column body — a tinted drop-zone surface holding the cards. Highlights while a
        // dragged card hovers over it.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(if (isDropTarget) accent.copy(alpha = 0.14f) else Mesha.colors.surfaceHover)
                .then(
                    if (isDropTarget) Modifier.border(1.dp, accent.copy(alpha = 0.6f), MaterialTheme.shapes.large)
                    else Modifier,
                )
                .padding(8.dp),
        ) {
            if (issues.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isDropTarget) "Drop here" else "No issues",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDropTarget) accent else Mesha.colors.textTertiary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(issues, key = { it.id }) { issue ->
                        BoardCard(
                            issue = issue,
                            moveTargets = allStatusNames.filter { it != issue.status },
                            isDragging = issue.id == draggingIssueId,
                            onClick = { onOpenIssue(issue.projectId, issue.id) },
                            onMove = { target -> onMove(issue.id, target) },
                            onDragStart = onDragStart,
                            onDragMove = onDragMove,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                }
            }
        }

        // Add-issue affordance
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onCreateIssue)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Mesha.colors.textTertiary, modifier = Modifier.size(16.dp))
            Text("Add issue", style = MaterialTheme.typography.labelMedium, color = Mesha.colors.textTertiary)
        }
    }
}

@Composable
private fun CountPill(count: Int, color: Color) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BoardCard(
    issue: IssueDto,
    moveTargets: List<String>,
    isDragging: Boolean,
    onClick: () -> Unit,
    onMove: (String) -> Unit,
    onDragStart: (issue: IssueDto, cardOrigin: Offset, cardSize: IntSize, grab: Offset) -> Unit,
    onDragMove: (pointer: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    var moveExpanded by remember { mutableStateOf(false) }
    // The card's own top-left in window coordinates, kept current so a drag can be reported
    // in the same coordinate space as the columns.
    var cardOrigin by remember { mutableStateOf(Offset.Zero) }
    MeshaCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardOrigin = it.positionInWindow() }
            .graphicsLayer { alpha = if (isDragging) 0.3f else 1f }
            .pointerInput(issue.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { grab -> onDragStart(issue, cardOrigin, size, grab) },
                    onDrag = { change, _ ->
                        change.consume()
                        onDragMove(cardOrigin + change.position)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Identifier + drag handle
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    issue.identifier ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Mesha.colors.textTertiary,
                )
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = Mesha.colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                issue.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Mesha.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Priority + labels
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PriorityTag(issue.priority)
                issue.labels.take(3).forEach { LabelChip(it.name, it.color) }
            }

            // Meta: relative time · PR badge · assignee avatar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatRelativeTime(issue.updatedAt ?: issue.createdAt) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mesha.colors.textTertiary,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    issue.lastPullRequest?.let { pr ->
                        Text(
                            prLabel(pr),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = prColor(pr),
                        )
                    }
                    AssigneeAvatar(issue.assignee?.name ?: issue.assignee?.email)
                }
            }

            // Move menu — a fallback for the long-press drag (and for accessibility).
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(Mesha.colors.surfaceHover)
                        .clickable(enabled = moveTargets.isNotEmpty()) { moveExpanded = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.OpenWith, contentDescription = null, tint = Mesha.colors.textSecondary, modifier = Modifier.size(14.dp))
                    Text(
                        "  Move",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Mesha.colors.textSecondary,
                    )
                }
                DropdownMenu(expanded = moveExpanded, onDismissRequest = { moveExpanded = false }) {
                    moveTargets.forEach { target ->
                        DropdownMenuItem(
                            text = { Text(target.replace('_', ' ')) },
                            onClick = {
                                moveExpanded = false
                                onMove(target)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A lightweight, elevated copy of a card rendered on top of the board and positioned under
 * the finger while dragging. The real card is dimmed in place; this is what the user sees move.
 */
@Composable
private fun DraggingCardOverlay(
    issue: IssueDto,
    widthPx: Int,
    density: Density,
    offset: Density.() -> IntOffset,
) {
    val widthDp = with(density) { widthPx.toDp() }
    Box(
        Modifier
            .offset(offset)
            .width(widthDp)
            .shadow(12.dp, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(Mesha.colors.surface)
            .border(1.dp, Mesha.colors.accent, MaterialTheme.shapes.medium)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                issue.identifier ?: "",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Mesha.colors.textTertiary,
            )
            Text(
                issue.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Mesha.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Priority as the web renders it on cards: a direction glyph + capitalized label, color-coded. */
@Composable
private fun PriorityTag(priority: String?) {
    if (priority.isNullOrBlank()) return
    // Theme-aware semantic colors (not raw hex) so contrast holds in light and dark modes.
    val (glyph, color) = when (priority.uppercase()) {
        "URGENT" -> "⚡" to Mesha.colors.destructive
        "HIGH" -> "↑" to Mesha.colors.warning
        "MEDIUM" -> "→" to Mesha.colors.accent
        "LOW" -> "↓" to Mesha.colors.textTertiary
        else -> "•" to Mesha.colors.textTertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(glyph, style = MaterialTheme.typography.labelMedium, color = color)
        Text(
            priority.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = if (priority.uppercase() == "URGENT") FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Small circular assignee avatar (initial), or a dashed ring when unassigned — matches the web. */
@Composable
private fun AssigneeAvatar(name: String?) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
    if (initial == null) {
        val ring = Mesha.colors.borderStrong
        Box(
            Modifier
                .size(24.dp)
                .drawBehind {
                    drawCircle(
                        color = ring,
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                        ),
                    )
                },
        )
    } else {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Mesha.colors.accentMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Mesha.colors.accentMutedText,
            )
        }
    }
}

/** Segmented list/board toggle mirroring the web view switcher. */
@Composable
private fun ViewModeSwitcher(
    mode: IssueViewMode,
    onSelect: (IssueViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Mesha.colors.surfaceHover)
            .border(1.dp, Mesha.colors.border, RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SwitcherSegment(
            selected = mode == IssueViewMode.LIST,
            icon = Icons.AutoMirrored.Filled.ListAlt,
            description = "List view",
            onClick = { onSelect(IssueViewMode.LIST) },
        )
        SwitcherSegment(
            selected = mode == IssueViewMode.BOARD,
            icon = Icons.Filled.ViewColumn,
            description = "Board view",
            onClick = { onSelect(IssueViewMode.BOARD) },
        )
    }
}

@Composable
private fun SwitcherSegment(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Mesha.colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) Color.White else Mesha.colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun prLabel(pr: GitHubPullRequestDto): String {
    val num = pr.githubPrNumber?.let { "#$it" } ?: "PR"
    return when {
        pr.mergedAt != null -> "$num merged"
        pr.state == "closed" -> "$num closed"
        else -> "$num open"
    }
}

@Composable
private fun prColor(pr: GitHubPullRequestDto): Color = when {
    pr.mergedAt != null -> PriorityPurple
    pr.state == "closed" -> Mesha.colors.destructive
    else -> Mesha.colors.success
}

// GitHub "merged" purple has no semantic equivalent in the palette, so keep it explicit.
private val PriorityPurple = Color(0xFF8B5CF6)

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
    MeshaCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
