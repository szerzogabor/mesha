package com.mesha.mobile.domain.ai

import kotlinx.serialization.Serializable

/**
 * The structured issue draft produced by an on-device model. This is the canonical
 * schema the rest of the app depends on; provider-specific quirks (e.g. Gemma's raw
 * text output) are normalized into this shape by the provider before it is returned.
 *
 * Required JSON schema (per product spec):
 * ```
 * { "title": "", "description": "", "acceptanceCriteria": [], "priority": "", "labels": [] }
 * ```
 */
@Serializable
data class IssueDraft(
    val title: String,
    val description: String,
    val acceptanceCriteria: List<String> = emptyList(),
    val priority: IssuePriority = IssuePriority.MEDIUM,
    val labels: List<String> = emptyList(),
)

/** Mirrors the backend `IssuePriority` enum so drafts map cleanly onto issue creation. */
@Serializable
enum class IssuePriority {
    LOW, MEDIUM, HIGH, URGENT;

    companion object {
        /** Lenient parse: accepts any casing / unknown values, defaulting to MEDIUM. */
        fun fromLenient(raw: String?): IssuePriority {
            if (raw.isNullOrBlank()) return MEDIUM
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: MEDIUM
        }
    }
}

/** Input for a draft generation request. */
data class GenerateIssueRequest(
    val prompt: String,
    /** Optional known project labels, passed to the model so it can pick existing ones. */
    val availableLabels: List<String> = emptyList(),
)
