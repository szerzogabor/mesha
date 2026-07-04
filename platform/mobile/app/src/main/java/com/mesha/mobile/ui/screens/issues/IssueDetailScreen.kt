package com.mesha.mobile.ui.screens.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.data.remote.dto.BlocksSessionDto
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.GitHubPullRequestDto
import com.mesha.mobile.ui.components.BadgeTone
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState
import com.mesha.mobile.ui.components.MeshaCard
import com.mesha.mobile.ui.components.MeshaTopAppBar
import com.mesha.mobile.ui.components.StatusBadge
import com.mesha.mobile.ui.theme.Mesha
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val PRIORITIES = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
private val FALLBACK_STATUSES = listOf("BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE")
private val ACTIVE_SESSION_STATES = setOf("CREATED", "DISPATCHING", "PLANNING", "EXECUTING", "WAITING_REVIEW", "PR_OPENED")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    projectId: String,
    issueId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(projectId, issueId) { viewModel.load(projectId, issueId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    LaunchedEffect(state.updateError) {
        state.updateError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUpdateError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MeshaTopAppBar(
                title = "",
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null && state.issue == null ->
                ErrorState(state.error!!, Modifier.padding(padding)) { viewModel.load(projectId, issueId) }
            else -> {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TitleHeader(state = state, viewModel = viewModel)
                    DescriptionCard(state = state, viewModel = viewModel)
                    CommentsActivityCard(state = state, viewModel = viewModel)
                    MetadataCard(state = state, viewModel = viewModel)
                    LinkedIssuesCard()
                    AttachmentsCard(state = state, viewModel = viewModel)
                    AiSessionsCard(state = state, viewModel = viewModel)
                    ResourcesCard(state = state)
                    TimestampsCard(state = state)
                    DangerZoneCard(viewModel = viewModel)
                }
            }
        }
    }

    if (state.showLabelEditor) LabelEditorDialog(state, viewModel)
    if (state.showStartSession) StartSessionDialog(state, viewModel)
    if (state.showDeleteConfirm) DeleteConfirmDialog(viewModel)
}

// ── Section label (uppercase, tracked) ───────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Mesha.colors.textTertiary,
        letterSpacing = 0.6.sp,
    )
}

// ── Title + timestamps ───────────────────────────────────────────────────────

@Composable
private fun TitleHeader(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val issue = state.issue ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(issue.title) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = draft.isBlank(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.updateTitle(draft)
                    editing = false
                }, enabled = draft.isNotBlank()) { Text("Save") }
                TextButton(onClick = { draft = issue.title; editing = false }) { Text("Cancel") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                issue.identifier?.let { IdentifierChip(it) }
                Text(
                    issue.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Mesha.colors.textPrimary,
                    modifier = Modifier.clickable { draft = issue.title; editing = true },
                )
            }
        }
        val created = relativeTime(issue.createdAt)
        val updated = relativeTime(issue.updatedAt)
        if (created != null || updated != null) {
            Text(
                listOfNotNull(
                    created?.let { "Created $it" },
                    updated?.let { "Updated $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Mesha.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun IdentifierChip(identifier: String) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .clip(shape)
            .background(Mesha.colors.surfaceHover)
            .border(1.dp, Mesha.colors.border, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            identifier,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = Mesha.colors.textTertiary,
        )
    }
}

// ── Description ──────────────────────────────────────────────────────────────

@Composable
private fun DescriptionCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val issue = state.issue ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(issue.description.orEmpty()) }

    MeshaCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Description")
            if (!editing) {
                Text(
                    "Edit",
                    style = MaterialTheme.typography.labelMedium,
                    color = Mesha.colors.textTertiary,
                    modifier = Modifier.clickable { draft = issue.description.orEmpty(); editing = true },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.updateDescription(draft); editing = false }) { Text("Save") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else {
            val description = issue.description?.takeIf { it.isNotBlank() }
            Text(
                description ?: "No description. Tap to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (description != null) Mesha.colors.textSecondary else Mesha.colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { draft = issue.description.orEmpty(); editing = true },
            )
        }
    }
}

// ── Comments + Activity tabs ─────────────────────────────────────────────────

@Composable
private fun CommentsActivityCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    MeshaCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TabButton("Comments (${state.comments.size})", tab == 0) { tab = 0 }
            TabButton("Activity (${state.activity.size})", tab == 1) { tab = 1 }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 12.dp), color = Mesha.colors.border)
        if (tab == 0) CommentsSection(state, viewModel) else ActivitySection(state)
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) Mesha.colors.accent else Mesha.colors.textTertiary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            Modifier
                .height(2.dp)
                .width(if (selected) 40.dp else 0.dp)
                .background(Mesha.colors.accent),
        )
    }
}

@Composable
private fun CommentsSection(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.comments.isEmpty()) {
            Text(
                "No comments yet",
                style = MaterialTheme.typography.bodySmall,
                color = Mesha.colors.textTertiary,
            )
        }
        state.comments.forEach { CommentCard(it, depth = 0, viewModel = viewModel) }

        state.replyingToId?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Replying to comment",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mesha.colors.accent,
                )
                TextButton(onClick = { viewModel.startReply(null) }) { Text("Cancel") }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.commentInput,
                onValueChange = { viewModel.setCommentInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (state.replyingToId != null) "Write a reply…" else "Add a comment…") },
                enabled = !state.sendingComment,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!state.sendingComment) viewModel.sendComment() }),
            )
            IconButton(
                onClick = { viewModel.sendComment() },
                enabled = state.commentInput.isNotBlank() && !state.sendingComment,
            ) {
                if (state.sendingComment) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Icon(Icons.Default.Send, contentDescription = "Send comment")
            }
        }
    }
}

@Composable
private fun CommentCard(comment: CommentDto, depth: Int, viewModel: IssueDetailViewModel) {
    val startPadding = minOf(depth, 4) * 12
    MeshaCard(
        Modifier
            .fillMaxWidth()
            .padding(start = startPadding.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                comment.author?.name ?: comment.author?.email ?: "Unknown",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Mesha.colors.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.startReply(comment.id) }) { Text("Reply") }
                IconButton(onClick = { viewModel.deleteComment(comment.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete comment", modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(comment.body, style = MaterialTheme.typography.bodyMedium, color = Mesha.colors.textSecondary)
    }
    comment.replies.forEach { CommentCard(it, depth = depth + 1, viewModel = viewModel) }
}

@Composable
private fun ActivitySection(state: IssueDetailUiState) {
    if (state.activity.isEmpty()) {
        Text("No activity yet", style = MaterialTheme.typography.bodySmall, color = Mesha.colors.textTertiary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.activity.take(30).forEach { event ->
            val who = event.user?.name ?: event.user?.email ?: "System"
            val what = event.eventType.replace('_', ' ').lowercase()
            val detail = listOfNotNull(event.oldValue, event.newValue)
                .filter { it.isNotBlank() }.joinToString(" → ")
            Text(
                buildString {
                    append("$who · $what")
                    if (detail.isNotBlank()) append(" ($detail)")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Mesha.colors.textTertiary,
            )
        }
    }
}

// ── Metadata: status / priority / assignee / labels ──────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val issue = state.issue ?: return
    MeshaCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Status
            SelectField(
                label = "Status",
                value = issue.status?.replace('_', ' ') ?: "No status",
                loading = state.updatingStatus,
                expanded = state.showStatusPicker,
                onExpand = { viewModel.showStatusPicker() },
                onDismiss = { viewModel.dismissStatusPicker() },
            ) {
                val options = state.statuses.map { it.name }.ifEmpty { FALLBACK_STATUSES }
                options.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name.replace('_', ' ')) },
                        onClick = { viewModel.updateStatus(name) },
                    )
                }
            }

            // Priority
            SelectField(
                label = "Priority",
                value = issue.priority ?: "No priority",
                loading = state.updatingPriority,
                expanded = state.showPriorityPicker,
                onExpand = { viewModel.showPriorityPicker() },
                onDismiss = { viewModel.dismissPriorityPicker() },
            ) {
                PRIORITIES.forEach { priority ->
                    DropdownMenuItem(text = { Text(priority) }, onClick = { viewModel.updatePriority(priority) })
                }
            }

            // Assignee (a human OR an AI agent)
            val assigneeText = when {
                state.issueAgents.isNotEmpty() ->
                    state.issueAgents.first().agentTitle ?: state.issueAgents.first().agentName ?: "Agent"
                issue.assignee != null -> issue.assignee.name ?: issue.assignee.email ?: "Assigned"
                else -> "Unassigned"
            }
            SelectField(
                label = "Assignee",
                value = assigneeText,
                loading = state.updatingAssignee,
                expanded = state.showAssigneePicker,
                onExpand = { viewModel.showAssigneePicker() },
                onDismiss = { viewModel.dismissAssigneePicker() },
            ) {
                DropdownMenuItem(text = { Text("Unassigned") }, onClick = { viewModel.assignTo(null) })
                if (state.members.isNotEmpty()) {
                    state.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.name ?: member.email ?: "Unknown") },
                            onClick = { viewModel.assignTo(member.userId) },
                        )
                    }
                }
                val agents = state.activeAgents
                if (agents.isNotEmpty()) {
                    HorizontalDivider(color = Mesha.colors.border)
                    agents.forEach { agent ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(agent.title ?: agent.name ?: "Agent")
                                    Text(
                                        "AI agent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Mesha.colors.textTertiary,
                                    )
                                }
                            },
                            onClick = { viewModel.assignAgent(agent.id) },
                        )
                    }
                }
                if (state.members.isEmpty() && agents.isEmpty()) {
                    DropdownMenuItem(text = { Text("Loading…") }, onClick = {}, enabled = false)
                }
            }

            // Labels
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Labels")
                    Text(
                        "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mesha.colors.textTertiary,
                        modifier = Modifier.clickable { viewModel.showLabelEditor() },
                    )
                }
                if (issue.labels.isEmpty()) {
                    Text("No labels", style = MaterialTheme.typography.bodySmall, color = Mesha.colors.textTertiary)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        issue.labels.forEach { LabelChip(it.name, it.color) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectField(
    label: String,
    value: String,
    loading: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, Mesha.colors.border, shape)
                    .background(Mesha.colors.surface)
                    .clickable { onExpand() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = Mesha.colors.textPrimary)
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Mesha.colors.textTertiary,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) { menuContent() }
        }
    }
}

// ── Linked issues (empty state, mirrors the web section) ─────────────────────

@Composable
private fun LinkedIssuesCard() {
    MeshaCard(Modifier.fillMaxWidth()) {
        SectionLabel("Linked Issues")
        Spacer(Modifier.height(8.dp))
        Text("No linked issues.", style = MaterialTheme.typography.bodySmall, color = Mesha.colors.textTertiary)
    }
}

// ── Attachments ──────────────────────────────────────────────────────────────

@Composable
private fun AttachmentsCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    MeshaCard(Modifier.fillMaxWidth()) {
        SectionLabel("Attachments")
        Spacer(Modifier.height(8.dp))
        if (state.attachments.isEmpty()) {
            Text("No attachments.", style = MaterialTheme.typography.bodySmall, color = Mesha.colors.textTertiary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.attachments.forEach { attachment ->
                    val opening = state.openingAttachmentId == attachment.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.openingAttachmentId == null) { viewModel.openAttachment(attachment) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (opening) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = Mesha.colors.textTertiary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium)
                            val meta = listOfNotNull(formatBytes(attachment.fileSize), attachment.uploadedByName)
                                .joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, style = MaterialTheme.typography.labelSmall, color = Mesha.colors.textTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── AI sessions ──────────────────────────────────────────────────────────────

@Composable
private fun AiSessionsCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    MeshaCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("AI Sessions")
                if (state.blocksSessions.isNotEmpty()) {
                    val n = state.blocksSessions.size
                    Text(
                        "$n session${if (n != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mesha.colors.textTertiary,
                    )
                }
            }
            if (state.blocksSessions.isEmpty()) {
                Text(
                    "No sessions yet. Start one to delegate implementation to an AI agent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesha.colors.textTertiary,
                )
            }
            state.blocksSessions.forEachIndexed { index, session ->
                SessionRow(
                    number = state.blocksSessions.size - index,
                    session = session,
                    onCancel = { viewModel.cancelSession(session.id) },
                )
            }
            Button(
                onClick = { viewModel.showStartSession() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Mesha.colors.accent),
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.blocksSessions.isEmpty()) "Start AI Session" else "Start New Session")
            }
        }
    }
}

@Composable
private fun SessionRow(number: Int, session: BlocksSessionDto, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Mesha.colors.border, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "#$number",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Mesha.colors.textTertiary,
            )
            Text(
                providerLabel(session.provider),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Mesha.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            val (label, tone) = sessionBadge(session.executionState)
            StatusBadge(label, tone)
            sessionPr(session)?.let { pr ->
                StatusBadge("PR ${prStateLabel(pr)}", prBadgeTone(pr))
            }
        }
        session.branchName?.let {
            Text(
                "Branch: $it",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Mesha.colors.textTertiary,
            )
        }
        session.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Mesha.colors.destructive)
        }
        collectSessionPrs(session).forEach { pr ->
            val url = pr.htmlUrl ?: return@forEach
            Text(
                "View PR${pr.githubPrNumber?.let { " #$it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Mesha.colors.accent,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { uriHandler.openUri(url) },
            )
        }
        if ((session.executionState?.uppercase() ?: "") in ACTIVE_SESSION_STATES) {
            TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) { Text("Cancel session") }
        }
    }
}

// ── Resources (PRs / branches produced by sessions) ──────────────────────────

@Composable
private fun ResourcesCard(state: IssueDetailUiState) {
    val prs = collectPullRequests(state)
    if (prs.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    MeshaCard(Modifier.fillMaxWidth()) {
        SectionLabel("Resources")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            prs.forEach { pr ->
                val url = pr.htmlUrl ?: return@forEach
                val shape = RoundedCornerShape(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, Mesha.colors.border, shape)
                        .clickable { uriHandler.openUri(url) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(prStateLabel(pr), prBadgeTone(pr))
                    Column(Modifier.weight(1f)) {
                        Text(
                            pr.title?.takeIf { it.isNotBlank() }
                                ?: pr.sourceBranch
                                ?: "Pull Request${pr.githubPrNumber?.let { " #$it" } ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Mesha.colors.textPrimary,
                        )
                        val meta = listOfNotNull(
                            pr.githubPrNumber?.let { "#$it" },
                            pr.sourceBranch,
                            pr.checksStatus?.let { "checks: $it" },
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = Mesha.colors.textTertiary)
                        }
                    }
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = Mesha.colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ── Timestamps ───────────────────────────────────────────────────────────────

@Composable
private fun TimestampsCard(state: IssueDetailUiState) {
    val issue = state.issue ?: return
    val created = absoluteTime(issue.createdAt)
    val updated = absoluteTime(issue.updatedAt)
    if (created == null && updated == null) return
    MeshaCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            created?.let { TimestampRow("Created:", it) }
            updated?.let { TimestampRow("Updated:", it) }
        }
    }
}

@Composable
private fun TimestampRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Mesha.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.labelSmall, color = Mesha.colors.textTertiary)
    }
}

// ── Danger zone ──────────────────────────────────────────────────────────────

@Composable
private fun DangerZoneCard(viewModel: IssueDetailViewModel) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Mesha.colors.destructive.copy(alpha = 0.4f), shape)
            .background(Mesha.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel("Danger Zone")
        OutlinedButton(
            onClick = { viewModel.showDeleteConfirm() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mesha.colors.destructive),
        ) {
            Text("Delete ticket")
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LabelEditorDialog(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val currentIds = state.issue?.labels?.map { it.id }?.toSet() ?: emptySet()
    AlertDialog(
        onDismissRequest = { viewModel.dismissLabelEditor() },
        confirmButton = { TextButton(onClick = { viewModel.dismissLabelEditor() }) { Text("Done") } },
        title = { Text("Labels") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.workspaceLabels.isEmpty()) {
                    Text("No labels in this workspace yet.", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.workspaceLabels.forEach { label ->
                        FilterChip(
                            selected = currentIds.contains(label.id),
                            onClick = { viewModel.toggleLabel(label.id) },
                            label = { Text(label.name) },
                            enabled = !state.updatingLabels,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.newLabelName,
                        onValueChange = viewModel::setNewLabelName,
                        label = { Text("New label") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = { viewModel.createLabel() },
                        enabled = state.newLabelName.isNotBlank() && !state.updatingLabels,
                    ) { Text("Add") }
                }
            }
        },
    )
}

@Composable
private fun StartSessionDialog(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissStartSession() },
        confirmButton = {
            Button(onClick = { viewModel.startSession() }, enabled = !state.startingSession) {
                if (state.startingSession) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(16.dp))
                }
                Text("Start")
            }
        },
        dismissButton = { TextButton(onClick = { viewModel.dismissStartSession() }) { Text("Cancel") } },
        title = { Text("Start AI session") },
        text = {
            OutlinedTextField(
                value = state.sessionInstructions,
                onValueChange = viewModel::setSessionInstructions,
                label = { Text("Instructions (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
        },
    )
}

@Composable
private fun DeleteConfirmDialog(viewModel: IssueDetailViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDeleteConfirm() },
        confirmButton = {
            Button(
                onClick = { viewModel.deleteIssue() },
                colors = ButtonDefaults.buttonColors(containerColor = Mesha.colors.destructive),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { viewModel.dismissDeleteConfirm() }) { Text("Cancel") } },
        title = { Text("Delete ticket?") },
        text = { Text("This permanently deletes the ticket and its comments. This cannot be undone.") },
    )
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun providerLabel(provider: String?): String =
    if (provider == "blocks") "Blocks AI" else provider ?: "AI Session"

private fun sessionBadge(stateRaw: String?): Pair<String, BadgeTone> = when (stateRaw?.uppercase()) {
    "DONE" -> "Done" to BadgeTone.Success
    "FAILED" -> "Failed" to BadgeTone.Destructive
    "CANCELED" -> "Canceled" to BadgeTone.Neutral
    "CREATED", "DISPATCHING" -> "Starting" to BadgeTone.Accent
    "PLANNING" -> "Planning" to BadgeTone.Accent
    "EXECUTING" -> "Coding" to BadgeTone.Accent
    "WAITING_REVIEW" -> "Awaiting review" to BadgeTone.Accent
    "PR_OPENED" -> "PR opened" to BadgeTone.Accent
    else -> (stateRaw?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown") to BadgeTone.Accent
}

private fun prStateLabel(pr: GitHubPullRequestDto): String = when {
    pr.state == "open" -> if (pr.draft == true) "Draft" else "Open"
    pr.mergedAt != null -> "Merged"
    pr.state == "closed" -> "Closed"
    else -> "PR"
}

private fun prBadgeTone(pr: GitHubPullRequestDto): BadgeTone = when {
    pr.state == "open" -> if (pr.draft == true) BadgeTone.Neutral else BadgeTone.Success
    pr.mergedAt != null -> BadgeTone.Accent
    pr.state == "closed" -> BadgeTone.Destructive
    else -> BadgeTone.Accent
}

/** PRs linked to a single session (falling back to the legacy prUrl field). */
private fun collectSessionPrs(session: BlocksSessionDto): List<GitHubPullRequestDto> =
    session.linkedPullRequests.ifEmpty {
        if (session.prUrl != null) listOf(
            GitHubPullRequestDto(id = session.id, githubPrNumber = session.prNumber, htmlUrl = session.prUrl),
        ) else emptyList()
    }

/** The single PR to summarize in a session header badge. */
private fun sessionPr(session: BlocksSessionDto): GitHubPullRequestDto? = collectSessionPrs(session).firstOrNull()

/**
 * Every pull request associated with the issue (issue's last PR + PRs from its sessions),
 * deduplicated by URL. Powers the Resources section.
 */
private fun collectPullRequests(state: IssueDetailUiState): List<GitHubPullRequestDto> {
    val fromSessions = state.blocksSessions.flatMap { collectSessionPrs(it) }
    return (listOfNotNull(state.issue?.lastPullRequest) + fromSessions)
        .filter { it.htmlUrl != null }
        .distinctBy { it.htmlUrl ?: it.githubPrNumber?.toString() ?: it.id }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC) }.getOrNull()
}

private fun relativeTime(iso: String?): String? {
    val instant = parseInstant(iso) ?: return null
    val minutes = (System.currentTimeMillis() - instant.toEpochMilli()) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}d ago"
        else -> absoluteDate(instant)
    }
}

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

private fun absoluteDate(instant: Instant): String = DATE_FORMAT.format(instant)

private fun absoluteTime(iso: String?): String? = parseInstant(iso)?.let { DATE_TIME_FORMAT.format(it) }
