package com.mesha.mobile.ui.screens.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mesha.mobile.data.remote.dto.BlocksSessionDto
import com.mesha.mobile.data.remote.dto.CommentDto
import com.mesha.mobile.data.remote.dto.GitHubPullRequestDto
import com.mesha.mobile.ui.components.ErrorState
import com.mesha.mobile.ui.components.LoadingState

private val PRIORITIES = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
private val ACTIVE_SESSION_STATES = setOf("CREATED", "DISPATCHING", "PLANNING", "EXECUTING", "WAITING_REVIEW", "PR_OPENED")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
            TopAppBar(
                title = { Text(state.issue?.identifier ?: "Issue") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (!state.editMode && state.issue != null) {
                        IconButton(onClick = { viewModel.startEdit() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit issue")
                        }
                        OverflowMenu(onDelete = { viewModel.showDeleteConfirm() })
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null && state.issue == null ->
                ErrorState(state.error!!, Modifier.padding(padding)) { viewModel.load(projectId, issueId) }
            else -> {
                val issue = state.issue
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.editMode) {
                        EditForm(state = state, viewModel = viewModel)
                    } else {
                        issue?.title?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!state.editMode) {
                        // Status & priority chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(state = state, viewModel = viewModel)
                            PriorityChip(state = state, viewModel = viewModel)
                        }

                        PullRequestsCard(state = state)

                        DetailsCard(state = state, viewModel = viewModel)

                        issue?.description?.takeIf { it.isNotBlank() }?.let { description ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Description", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }

                        AttachmentsCard(state = state, viewModel = viewModel)
                        AiAgentsCard(state = state, viewModel = viewModel)
                        AiSessionsCard(state = state, viewModel = viewModel)
                        ActivityCard(state = state)
                    }

                    HorizontalDivider()
                    CommentsSection(state = state, viewModel = viewModel)
                }
            }
        }
    }

    // Dialogs
    if (state.showAssigneePicker) AssigneeDialog(state, viewModel)
    if (state.showLabelEditor) LabelEditorDialog(state, viewModel)
    if (state.showAgentPicker) AgentPickerDialog(state, viewModel)
    if (state.showStartSession) StartSessionDialog(state, viewModel)
    if (state.showDeleteConfirm) DeleteConfirmDialog(viewModel)
}

@Composable
private fun OverflowMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Delete issue") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun EditForm(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    OutlinedTextField(
        value = state.editTitle,
        onValueChange = viewModel::setEditTitle,
        label = { Text("Title") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = state.editTitle.isBlank(),
    )
    OutlinedTextField(
        value = state.editDescription,
        onValueChange = viewModel::setEditDescription,
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(onClick = { viewModel.cancelEdit() }, enabled = !state.submittingEdit) { Text("Cancel") }
        Button(
            onClick = { viewModel.submitEdit() },
            enabled = !state.submittingEdit && state.editTitle.isNotBlank(),
        ) {
            if (state.submittingEdit) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            }
            Text("Save")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChip(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val issue = state.issue
    val statusColor = state.statuses.firstOrNull { it.name == issue?.status }?.color
    Box {
        AssistChip(
            onClick = { viewModel.showStatusPicker() },
            leadingIcon = { StatusDot(statusColor) },
            label = {
                if (state.updatingStatus) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(issue?.status ?: "No status")
            },
        )
        DropdownMenu(
            expanded = state.showStatusPicker,
            onDismissRequest = { viewModel.dismissStatusPicker() },
        ) {
            val options = state.statuses.map { it.name to it.color }
                .ifEmpty { listOf("BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE").map { it to null } }
            options.forEach { (name, color) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { StatusDot(color) },
                    onClick = { viewModel.updateStatus(name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityChip(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    Box {
        AssistChip(
            onClick = { viewModel.showPriorityPicker() },
            label = {
                if (state.updatingPriority) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(state.issue?.priority ?: "No priority")
            },
        )
        DropdownMenu(
            expanded = state.showPriorityPicker,
            onDismissRequest = { viewModel.dismissPriorityPicker() },
        ) {
            PRIORITIES.forEach { priority ->
                DropdownMenuItem(text = { Text(priority) }, onClick = { viewModel.updatePriority(priority) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val issue = state.issue
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Details", fontWeight = FontWeight.SemiBold)

            // Assignee (tap to change)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Assignee", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.showAssigneePicker() }, enabled = !state.updatingAssignee) {
                    if (state.updatingAssignee) CircularProgressIndicator(strokeWidth = 2.dp)
                    else Text(issue?.assignee?.name ?: issue?.assignee?.email ?: "Unassigned")
                }
            }

            // Labels (tap to edit)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Labels", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.showLabelEditor() }) { Text("Edit") }
            }
            if (issue?.labels?.isNotEmpty() == true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    issue.labels.forEach { LabelChip(it.name, it.color) }
                }
            }

            issue?.createdAt?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Created", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Collect every pull request associated with the issue: the issue's most-recent PR plus any
 * PRs surfaced by its AI sessions. Deduplicated by URL (falling back to PR number) so a PR that
 * appears in both places is shown once. This is what powers the direct "open PR" links.
 */
private fun collectPullRequests(state: IssueDetailUiState): List<GitHubPullRequestDto> {
    val fromSessions = state.blocksSessions.flatMap { session ->
        session.linkedPullRequests.ifEmpty {
            if (session.prUrl != null) listOf(
                GitHubPullRequestDto(
                    id = session.id,
                    githubPrNumber = session.prNumber,
                    htmlUrl = session.prUrl,
                ),
            ) else emptyList()
        }
    }
    val all = listOfNotNull(state.issue?.lastPullRequest) + fromSessions
    return all
        .filter { it.htmlUrl != null }
        .distinctBy { it.htmlUrl ?: it.githubPrNumber?.toString() ?: it.id }
}

@Composable
private fun PullRequestsCard(state: IssueDetailUiState) {
    val prs = collectPullRequests(state)
    if (prs.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pull Requests", fontWeight = FontWeight.SemiBold)
            prs.forEach { pr ->
                val url = pr.htmlUrl ?: return@forEach
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri(url) },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        val heading = buildString {
                            append("PR")
                            pr.githubPrNumber?.let { append(" #$it") }
                            pr.title?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        }
                        Text(
                            heading,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                        val meta = listOfNotNull(
                            pr.state?.takeIf { it.isNotBlank() },
                            pr.checksStatus?.let { "checks: $it" },
                            if (pr.draft == true) "draft" else null,
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentsCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    if (state.attachments.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Attachments", fontWeight = FontWeight.SemiBold)
            state.attachments.forEach { attachment ->
                val opening = state.openingAttachmentId == attachment.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state.openingAttachmentId == null) {
                            viewModel.openAttachment(attachment)
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (opening) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium)
                        val meta = listOfNotNull(
                            formatBytes(attachment.fileSize),
                            attachment.uploadedByName,
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun AiAgentsCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI Agents", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { viewModel.showAgentPicker() }, enabled = !state.updatingAgents) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Assign")
                }
            }
            if (state.issueAgents.isEmpty()) {
                Text("No agents assigned", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.issueAgents.forEach { agent ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.SmartToy, contentDescription = null)
                        Column {
                            Text(agent.agentTitle ?: agent.agentName ?: "Agent",
                                style = MaterialTheme.typography.bodyMedium)
                            agent.providerType?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.unassignAgent(agent.agentDefinitionId) }) {
                        Icon(Icons.Default.Close, contentDescription = "Unassign")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSessionsCard(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI Sessions", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { viewModel.showStartSession() }) { Text("Start") }
            }
            if (state.blocksSessions.isEmpty()) {
                Text("No AI sessions yet", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.blocksSessions.forEach { session ->
                SessionRow(session = session, onCancel = { viewModel.cancelSession(session.id) })
            }
        }
    }
}

@Composable
private fun SessionRow(session: BlocksSessionDto, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExecutionStateBadge(session.executionState)
            val active = (session.executionState?.uppercase() ?: "") in ACTIVE_SESSION_STATES
            if (active) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        session.branchName?.let {
            Text("Branch: $it", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        session.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        val uriHandler = LocalUriHandler.current
        val prs = session.linkedPullRequests.ifEmpty {
            if (session.prUrl != null) listOf(
                GitHubPullRequestDto(
                    id = session.id, githubPrNumber = session.prNumber, htmlUrl = session.prUrl,
                ),
            ) else emptyList()
        }
        prs.forEach { pr ->
            val prModifier = pr.htmlUrl?.let { Modifier.clickable { uriHandler.openUri(it) } } ?: Modifier
            Row(
                modifier = prModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("PR #${pr.githubPrNumber ?: ""} ${pr.state ?: ""}".trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = if (pr.htmlUrl != null) TextDecoration.Underline else null)
                pr.checksStatus?.let { checks ->
                    Text("· checks: $checks", style = MaterialTheme.typography.labelSmall,
                        color = if (checks.equals("success", true)) StatusGreen
                        else if (checks.equals("failure", true) || checks.equals("error", true))
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ExecutionStateBadge(stateRaw: String?) {
    val label = stateRaw?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    val color = when (stateRaw?.uppercase()) {
        "DONE" -> StatusGreen
        "FAILED", "CANCELED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Medium)
}

@Composable
private fun ActivityCard(state: IssueDetailUiState) {
    if (state.activity.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Activity", fontWeight = FontWeight.SemiBold)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CommentsSection(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    Text("Comments", fontWeight = FontWeight.SemiBold)
    if (state.comments.isEmpty()) {
        Text("No comments yet", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.comments.forEach { comment ->
        CommentCard(comment = comment, depth = 0, viewModel = viewModel)
    }

    // Reply indicator
    state.replyingToId?.let {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Replying to comment", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
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
            label = { Text(if (state.replyingToId != null) "Write a reply" else "Add a comment") },
            enabled = !state.sendingComment,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.sendComment() }),
        )
        IconButton(
            onClick = { viewModel.sendComment() },
            enabled = state.commentInput.isNotBlank() && !state.sendingComment,
        ) {
            if (state.sendingComment) CircularProgressIndicator(strokeWidth = 2.dp)
            else Icon(Icons.Default.Send, contentDescription = "Send comment")
        }
    }
}

@Composable
private fun CommentCard(comment: CommentDto, depth: Int, viewModel: IssueDetailViewModel) {
    // Cap indentation depth so deeply-nested reply chains can't push the card off-screen.
    val startPadding = minOf(depth, 4) * 12
    Card(Modifier.fillMaxWidth().padding(start = startPadding.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                comment.author?.name?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row {
                    TextButton(onClick = { viewModel.startReply(comment.id) }) { Text("Reply") }
                    IconButton(onClick = { viewModel.deleteComment(comment.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete comment")
                    }
                }
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
    comment.replies.forEach { reply ->
        CommentCard(comment = reply, depth = depth + 1, viewModel = viewModel)
    }
}

// --- Dialogs ---

@Composable
private fun AssigneeDialog(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissAssigneePicker() },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { viewModel.dismissAssigneePicker() }) { Text("Close") } },
        title = { Text("Assignee") },
        text = {
            Column {
                DropdownMenuItem(text = { Text("Unassigned") }, onClick = { viewModel.assignTo(null) })
                state.members.forEach { member ->
                    DropdownMenuItem(
                        text = { Text(member.name ?: member.email ?: "Unknown") },
                        onClick = { viewModel.assignTo(member.userId) },
                    )
                }
                if (state.members.isEmpty()) {
                    Text("Loading members…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

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
private fun AgentPickerDialog(state: IssueDetailUiState, viewModel: IssueDetailViewModel) {
    val assignedIds = state.issueAgents.map { it.agentDefinitionId }.toSet()
    AlertDialog(
        onDismissRequest = { viewModel.dismissAgentPicker() },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { viewModel.dismissAgentPicker() }) { Text("Close") } },
        title = { Text("Assign AI agent") },
        text = {
            Column {
                val available = state.activeAgents.filter { !assignedIds.contains(it.id) }
                if (available.isEmpty()) {
                    Text("No available agents.", style = MaterialTheme.typography.bodySmall)
                }
                available.forEach { agent ->
                    DropdownMenuItem(
                        text = { Text(agent.title ?: agent.name ?: "Agent") },
                        onClick = { viewModel.assignAgent(agent.id) },
                    )
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
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
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
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { viewModel.dismissDeleteConfirm() }) { Text("Cancel") } },
        title = { Text("Delete issue?") },
        text = { Text("This permanently deletes the issue and its comments. This cannot be undone.") },
    )
}

private val StatusGreen = androidx.compose.ui.graphics.Color(0xFF2E7D32)
