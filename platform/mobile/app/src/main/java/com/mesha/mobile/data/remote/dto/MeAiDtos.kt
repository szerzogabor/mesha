package com.mesha.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** Prompt body for the per-user AI endpoints (`/api/me/ai/draft`, `/api/me/ai/complete`). */
@Serializable
data class MeAiPromptRequestDto(val prompt: String)

/**
 * Structured draft from `POST /api/me/ai/draft` — mirrors the backend `AIDraftContent`
 * record. `acceptanceCriteria` is a markdown string and `suggestedLabels` is a JSON array
 * string; the provider normalizes both into the app's `IssueDraft` shape.
 */
@Serializable
data class MeAiDraftResponseDto(
    val title: String = "",
    val description: String = "",
    val acceptanceCriteria: String = "",
    val suggestedLabels: String = "",
    val prioritySuggestion: String = "MEDIUM",
    val implementationNotes: String = "",
    val scopeNotes: String = "",
    val outOfScopeNotes: String = "",
)

/** Raw completion from `POST /api/me/ai/complete`. */
@Serializable
data class MeAiCompletionResponseDto(val text: String = "")

/**
 * Safe view of the user's OpenAI config from `GET /api/me/openai/config`. A 200 means
 * ChatGPT is configured; a 404 means it isn't. Only the fields the app needs are declared
 * (unknown keys are ignored by the Json config).
 */
@Serializable
data class MeOpenAiConfigDto(
    val authMode: String? = null,
    val status: String? = null,
)
