package com.mesha.mobile.domain.ai.agent

import com.mesha.mobile.data.remote.dto.CreateIssueRequestDto
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.data.remote.dto.LabelDto
import com.mesha.mobile.data.remote.dto.UpdateIssueRequestDto
import com.mesha.mobile.data.repository.MeshaRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The concrete [AgentTool]s that give the on-device chat agent read/write access to Mesha
 * tickets over the existing REST API (via [MeshaRepository]).
 *
 * Design notes for a small on-device model:
 *  - Tools accept human-friendly references (a ticket by identifier *or* title, labels/statuses/
 *    assignees by name/email) and resolve them to ids internally, so the model doesn't have to
 *    reliably chain "look up id, then act" — which weak models fail at.
 *  - No tool throws for an expected failure (no project selected, unknown ticket, API error);
 *    each returns a short, plain-language observation the model can reason about or relay.
 *  - Observations are kept compact — the on-device context window is small.
 */

// ---- shared argument helpers -------------------------------------------------

internal fun JsonObject.str(vararg keys: String): String? {
    for (key in keys) {
        val v = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()
        if (!v.isNullOrBlank()) return v
    }
    return null
}

/** Read a list argument that may arrive as a JSON array or a single comma/newline-delimited string. */
internal fun JsonObject.strList(vararg keys: String): List<String>? {
    for (key in keys) {
        when (val v = this[key]) {
            is JsonArray -> {
                val items = v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                    .filter { it.isNotBlank() }
                if (items.isNotEmpty()) return items
            }
            is JsonPrimitive -> {
                val text = v.contentOrNull.orEmpty()
                val items = text.split('\n', ',', ';').map { it.trim() }.filter { it.isNotBlank() }
                if (items.isNotEmpty()) return items
            }
            else -> {}
        }
    }
    return null
}

private const val NO_PROJECT =
    "No project is selected. Ask the user to open a project in the app first."
private const val NO_WORKSPACE =
    "No workspace is selected. Ask the user to open a workspace first."

private fun truncate(text: String?, max: Int): String {
    val t = text?.trim().orEmpty()
    return if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
}

private fun IssueDto.line(): String {
    val who = assignee?.name ?: assignee?.email ?: "unassigned"
    val ref = identifier ?: title
    return "$ref — $title [${status ?: "?"}, ${priority ?: "?"}, $who]"
}

/**
 * Find an issue in [projectId] by a free-form [ref]: exact identifier match first
 * (case-insensitive), then exact title, then title contains. Returns null if nothing matches.
 */
internal suspend fun MeshaRepository.resolveIssue(projectId: String, ref: String): IssueDto? {
    val needle = ref.trim().trimStart('#')
    val issues = getIssues(projectId, size = 100).getOrElse { return null }
    return issues.firstOrNull { it.identifier?.equals(needle, ignoreCase = true) == true }
        ?: issues.firstOrNull { it.title.equals(needle, ignoreCase = true) }
        ?: issues.firstOrNull { it.title.contains(needle, ignoreCase = true) }
}

private val VALID_PRIORITIES = setOf("LOW", "MEDIUM", "HIGH", "URGENT")

// ---- read tools --------------------------------------------------------------

class ListTicketsTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "list_tickets"
    override val description = "List tickets in the active project, optionally filtered."
    override val argsSpec =
        """{"status?":"name","search?":"text","assignee?":"name or email","limit?":15}"""

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val projectId = context.projectId ?: return NO_PROJECT
        val statusFilter = args.str("status")
        val searchFilter = args.str("search", "query", "q")
        val assigneeFilter = args.str("assignee")
        val limit = (args.str("limit")?.toIntOrNull() ?: 15).coerceIn(1, 30)

        val issues = repo.getIssues(projectId, size = 100)
            .getOrElse { return "Couldn't load tickets: ${it.message ?: "API error"}." }

        val filtered = issues.asSequence()
            .filter { statusFilter == null || it.status.equals(statusFilter, ignoreCase = true) }
            .filter { searchFilter == null || it.title.contains(searchFilter, ignoreCase = true) }
            .filter {
                assigneeFilter == null ||
                    it.assignee?.name?.contains(assigneeFilter, ignoreCase = true) == true ||
                    it.assignee?.email?.contains(assigneeFilter, ignoreCase = true) == true
            }
            .take(limit)
            .toList()

        if (filtered.isEmpty()) return "No tickets match."
        return buildString {
            appendLine("${filtered.size} ticket(s):")
            filtered.forEach { appendLine("- ${it.line()}") }
        }.trimEnd()
    }
}

class GetTicketTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "get_ticket"
    override val description = "Get the full details of one ticket by identifier or title."
    override val argsSpec = """{"ticket":"identifier or title"}"""

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val projectId = context.projectId ?: return NO_PROJECT
        val ref = args.str("ticket", "identifier", "id", "title")
            ?: return "Which ticket? Provide an identifier or title in \"ticket\"."
        val issue = repo.resolveIssue(projectId, ref)
            ?: return "No ticket found matching \"$ref\"."
        val labels = issue.labels.joinToString(", ") { it.name }.ifBlank { "none" }
        val who = issue.assignee?.name ?: issue.assignee?.email ?: "unassigned"
        return buildString {
            appendLine("${issue.identifier ?: "(no id)"}: ${issue.title}")
            appendLine("status: ${issue.status ?: "?"} | priority: ${issue.priority ?: "?"} | assignee: $who")
            appendLine("labels: $labels")
            appendLine("description: ${truncate(issue.description, 600).ifBlank { "(empty)" }}")
        }.trimEnd()
    }
}

class ListCommentsTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "list_comments"
    override val description = "List recent comments on a ticket."
    override val argsSpec = """{"ticket":"identifier or title","limit?":5}"""

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val projectId = context.projectId ?: return NO_PROJECT
        val ref = args.str("ticket", "identifier", "id", "title")
            ?: return "Which ticket? Provide an identifier or title in \"ticket\"."
        val issue = repo.resolveIssue(projectId, ref) ?: return "No ticket found matching \"$ref\"."
        val limit = (args.str("limit")?.toIntOrNull() ?: 5).coerceIn(1, 15)
        val comments = repo.getComments(issue.id)
            .getOrElse { return "Couldn't load comments: ${it.message ?: "API error"}." }
        if (comments.isEmpty()) return "${issue.identifier ?: ref} has no comments."
        return buildString {
            appendLine("Comments on ${issue.identifier ?: ref}:")
            comments.takeLast(limit).forEach {
                val author = it.author?.name ?: it.author?.email ?: "someone"
                appendLine("- $author: ${truncate(it.body, 200)}")
            }
        }.trimEnd()
    }
}

class ListLabelsTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "list_labels"
    override val description = "List the labels available in the workspace."
    override val argsSpec = "{}"

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val workspaceId = context.workspaceId ?: return NO_WORKSPACE
        val labels = repo.getLabels(workspaceId)
            .getOrElse { return "Couldn't load labels: ${it.message ?: "API error"}." }
        if (labels.isEmpty()) return "No labels defined in this workspace."
        return "Labels: " + labels.joinToString(", ") { it.name }
    }
}

class ListStatusesTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "list_statuses"
    override val description = "List the workflow statuses a ticket can be in for this project."
    override val argsSpec = "{}"

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val projectId = context.projectId ?: return NO_PROJECT
        val statuses = repo.getProjectStatuses(projectId)
            .getOrElse { return "Couldn't load statuses: ${it.message ?: "API error"}." }
        if (statuses.isEmpty()) return "No statuses defined for this project."
        return "Statuses: " + statuses.sortedBy { it.position }.joinToString(", ") { it.name }
    }
}

class ListMembersTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "list_members"
    override val description = "List people a ticket can be assigned to in the workspace."
    override val argsSpec = "{}"

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val workspaceId = context.workspaceId ?: return NO_WORKSPACE
        val members = repo.getWorkspaceMembers(workspaceId)
            .getOrElse { return "Couldn't load members: ${it.message ?: "API error"}." }
        if (members.isEmpty()) return "No members found."
        return "Members: " + members.joinToString(", ") { it.name ?: it.email ?: it.userId }
    }
}

// ---- write tools -------------------------------------------------------------

/** Resolve label names to ids against the workspace, reporting any that didn't match. */
private suspend fun MeshaRepository.resolveLabelIds(
    workspaceId: String,
    names: List<String>,
): Pair<List<String>, List<String>> {
    val all: List<LabelDto> = getLabels(workspaceId).getOrElse { emptyList() }
    val ids = mutableListOf<String>()
    val unknown = mutableListOf<String>()
    for (name in names) {
        val match = all.firstOrNull { it.name.equals(name.trim().trimStart('#'), ignoreCase = true) }
        if (match != null) ids.add(match.id) else unknown.add(name)
    }
    return ids to unknown
}

/** Resolve an assignee reference (name or email) to a workspace member's user id. */
private suspend fun MeshaRepository.resolveAssigneeId(workspaceId: String, ref: String): String? {
    val members = getWorkspaceMembers(workspaceId).getOrElse { return null }
    val needle = ref.trim()
    return members.firstOrNull { it.email.equals(needle, ignoreCase = true) }?.userId
        ?: members.firstOrNull { it.name.equals(needle, ignoreCase = true) }?.userId
        ?: members.firstOrNull { it.name?.contains(needle, ignoreCase = true) == true }?.userId
}

/** Resolve a status name to the project's exact stored spelling, or null if it doesn't exist. */
private suspend fun MeshaRepository.resolveStatusName(projectId: String, ref: String): String? {
    val statuses = getProjectStatuses(projectId).getOrElse { return null }
    return statuses.firstOrNull { it.name.equals(ref.trim(), ignoreCase = true) }?.name
}

private val CLEAR_ASSIGNEE_TOKENS = setOf("none", "unassign", "unassigned", "no one", "nobody", "clear")

class CreateTicketTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "create_ticket"
    override val description = "Create a new ticket in the active project."
    override val argsSpec =
        """{"title":"...","description?":"...","priority?":"LOW|MEDIUM|HIGH|URGENT","status?":"name","labels?":["name"],"assignee?":"name or email"}"""

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val projectId = context.projectId ?: return NO_PROJECT
        val title = args.str("title", "name", "summary")
            ?: return "A title is required to create a ticket (\"title\")."

        val priority = args.str("priority")?.uppercase()?.let {
            if (it in VALID_PRIORITIES) it else return "Priority must be one of ${VALID_PRIORITIES.joinToString(", ")}."
        }

        val notes = StringBuilder()

        val status = args.str("status")?.let { requested ->
            repo.resolveStatusName(projectId, requested)
                ?: return "Status \"$requested\" doesn't exist. Use list_statuses to see valid ones."
        }

        var labelIds: List<String>? = null
        args.strList("labels", "label")?.let { names ->
            val workspaceId = context.workspaceId ?: return NO_WORKSPACE
            val (ids, unknown) = repo.resolveLabelIds(workspaceId, names)
            labelIds = ids
            if (unknown.isNotEmpty()) notes.append(" (skipped unknown labels: ${unknown.joinToString(", ")})")
        }

        var assigneeId: String? = null
        args.str("assignee")?.let { ref ->
            if (ref.lowercase() !in CLEAR_ASSIGNEE_TOKENS) {
                val workspaceId = context.workspaceId ?: return NO_WORKSPACE
                assigneeId = repo.resolveAssigneeId(workspaceId, ref)
                    ?: return "Couldn't find a member matching \"$ref\". Use list_members."
            }
        }

        val body = CreateIssueRequestDto(
            title = title,
            description = args.str("description", "body"),
            status = status,
            priority = priority,
            assigneeId = assigneeId,
            labelIds = labelIds,
        )
        val created = repo.createIssue(projectId, body)
            .getOrElse { return "Couldn't create the ticket: ${it.message ?: "API error"}." }
        return "Created ${created.identifier ?: "ticket"}: ${created.title}.$notes".trim()
    }
}

class UpdateTicketTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "update_ticket"
    override val description =
        "Update a ticket: title, description, status, priority, labels or assignee."
    override val argsSpec =
        """{"ticket":"identifier or title","title?":"...","description?":"...","status?":"name","priority?":"LOW|MEDIUM|HIGH|URGENT","labels?":["name"],"add_labels?":["name"],"remove_labels?":["name"],"assignee?":"name, email or none"}"""

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val projectId = context.projectId ?: return NO_PROJECT
        val ref = args.str("ticket", "identifier", "id")
            ?: return "Which ticket? Provide an identifier or title in \"ticket\"."
        val issue = repo.resolveIssue(projectId, ref) ?: return "No ticket found matching \"$ref\"."

        val changes = mutableListOf<String>()
        val notes = StringBuilder()

        val newTitle = args.str("title")?.also { changes.add("title") }
        val newDescription = args.str("description", "body")?.also { changes.add("description") }

        val newStatus = args.str("status")?.let { requested ->
            val resolved = repo.resolveStatusName(projectId, requested)
                ?: return "Status \"$requested\" doesn't exist. Use list_statuses to see valid ones."
            changes.add("status")
            resolved
        }

        val newPriority = args.str("priority")?.uppercase()?.let {
            if (it !in VALID_PRIORITIES) return "Priority must be one of ${VALID_PRIORITIES.joinToString(", ")}."
            changes.add("priority")
            it
        }

        // Assignee: explicit clear tokens unassign; otherwise resolve name/email.
        var assigneeId: String? = null
        var clearAssignee: Boolean? = null
        args.str("assignee")?.let { assignee ->
            if (assignee.lowercase() in CLEAR_ASSIGNEE_TOKENS) {
                clearAssignee = true
                changes.add("assignee (cleared)")
            } else {
                val workspaceId = context.workspaceId ?: return NO_WORKSPACE
                assigneeId = repo.resolveAssigneeId(workspaceId, assignee)
                    ?: return "Couldn't find a member matching \"$assignee\". Use list_members."
                changes.add("assignee")
            }
        }

        // Labels: full replace via "labels", or incremental via add_labels/remove_labels.
        val labelIds: List<String>? = run {
            val workspaceId = context.workspaceId
            val replace = args.strList("labels")
            val add = args.strList("add_labels", "add_label")
            val remove = args.strList("remove_labels", "remove_label")
            if (replace == null && add == null && remove == null) return@run null
            if (workspaceId == null) return NO_WORKSPACE

            val target = LinkedHashSet<String>()
            if (replace != null) {
                val (ids, unknown) = repo.resolveLabelIds(workspaceId, replace)
                target.addAll(ids)
                if (unknown.isNotEmpty()) notes.append(" (skipped unknown labels: ${unknown.joinToString(", ")})")
            } else {
                target.addAll(issue.labels.map { it.id })
            }
            if (add != null) {
                val (ids, unknown) = repo.resolveLabelIds(workspaceId, add)
                target.addAll(ids)
                if (unknown.isNotEmpty()) notes.append(" (skipped unknown labels: ${unknown.joinToString(", ")})")
            }
            if (remove != null) {
                val (ids, _) = repo.resolveLabelIds(workspaceId, remove)
                target.removeAll(ids.toSet())
            }
            changes.add("labels")
            target.toList()
        }

        if (changes.isEmpty()) {
            return "Nothing to update. Provide a field like status, priority, labels or assignee."
        }

        val body = UpdateIssueRequestDto(
            title = newTitle,
            description = newDescription,
            status = newStatus,
            priority = newPriority,
            assigneeId = assigneeId,
            clearAssignee = clearAssignee,
            labelIds = labelIds,
        )
        val updated = repo.updateIssue(projectId, issue.id, body)
            .getOrElse { return "Couldn't update ${issue.identifier ?: ref}: ${it.message ?: "API error"}." }
        return "Updated ${updated.identifier ?: ref}: changed ${changes.joinToString(", ")}.$notes".trim()
    }
}

class AddCommentTool(private val repo: MeshaRepository) : AgentTool {
    override val name = "add_comment"
    override val description = "Add a comment to a ticket."
    override val argsSpec = """{"ticket":"identifier or title","body":"comment text"}"""

    override suspend fun execute(args: JsonObject, context: AgentContext): String {
        val projectId = context.projectId ?: return NO_PROJECT
        val ref = args.str("ticket", "identifier", "id")
            ?: return "Which ticket? Provide an identifier or title in \"ticket\"."
        val body = args.str("body", "comment", "text", "message")
            ?: return "What should the comment say? Provide \"body\"."
        val issue = repo.resolveIssue(projectId, ref) ?: return "No ticket found matching \"$ref\"."
        repo.addComment(issue.id, body)
            .getOrElse { return "Couldn't add the comment: ${it.message ?: "API error"}." }
        return "Added a comment to ${issue.identifier ?: ref}."
    }
}
