package com.mesha.mobile.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the raw, often-messy text a local LLM emits into a valid [IssueDraft].
 *
 * Local models frequently wrap JSON in markdown fences, add prose before/after the
 * object, emit `acceptanceCriteria` as a newline-delimited string instead of an array,
 * or use an out-of-range priority. This parser extracts the JSON, parses it leniently,
 * and repairs the common deviations so the provider can guarantee a well-formed draft.
 *
 * Pure and dependency-free so it can be unit-tested without an Android runtime.
 */
object IssueDraftParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * @param raw the model's raw textual output
     * @param fallbackTitleSource prompt text used to synthesize a title if the model omits one
     * @throws LocalAiException.InvalidOutput when no usable content can be recovered
     */
    fun parse(raw: String, fallbackTitleSource: String = ""): IssueDraft {
        val jsonText = extractJsonObject(raw)
            ?: throw LocalAiException.InvalidOutput("Model output contained no JSON object")

        val root: JsonObject = try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (e: Exception) {
            throw LocalAiException.InvalidOutput("Model output was not valid JSON: ${e.message}", e)
        }

        val title = root.string("title")?.trim().orEmptyTitle(fallbackTitleSource)
        val description = root.string("description")?.trim().orEmpty()
        val acceptanceCriteria = root["acceptanceCriteria"]?.toStringList()
            ?: root["acceptance_criteria"]?.toStringList()
            ?: emptyList()
        val priority = IssuePriority.fromLenient(root.string("priority"))
        val labels = root["labels"]?.toStringList() ?: emptyList()

        if (title.isBlank() && description.isBlank()) {
            throw LocalAiException.InvalidOutput("Model output had neither title nor description")
        }

        return IssueDraft(
            title = title.take(MAX_TITLE_LEN),
            description = description,
            acceptanceCriteria = acceptanceCriteria
                .map { it.trim().removePrefix("- ").trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_CRITERIA),
            priority = priority,
            labels = labels
                .map { it.trim().removePrefix("#").trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_LABELS),
        )
    }

    /**
     * Locate a JSON object inside arbitrary text. Handles ```json fenced blocks and
     * leading/trailing prose by scanning for the outermost balanced braces.
     */
    internal fun extractJsonObject(raw: String): String? {
        if (raw.isBlank()) return null
        val withoutFences = raw
            .replace("```json", "```")
            .substringAfter("```", raw)
            .substringBefore("```")
            .ifBlank { raw }

        val start = withoutFences.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until withoutFences.length) {
            val c = withoutFences[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return withoutFences.substring(start, i + 1)
                }
            }
        }
        return null // unbalanced
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    /** Accepts a JSON array, or a single string that may be newline/comma/semicolon delimited. */
    private fun JsonElement.toStringList(): List<String> = when (this) {
        is JsonArray -> jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> {
            val text = jsonPrimitive.contentOrNull.orEmpty()
            text.split('\n', ';', ',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        else -> emptyList()
    }

    private fun String?.orEmptyTitle(fallbackSource: String): String {
        if (!this.isNullOrBlank()) return this
        // Derive a concise title from the first sentence/line of the prompt.
        val firstLine = fallbackSource.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val firstSentence = firstLine.substringBefore('.').trim()
        return firstSentence.ifBlank { firstLine }.take(MAX_TITLE_LEN)
    }

    private const val MAX_TITLE_LEN = 140
    private const val MAX_CRITERIA = 12
    private const val MAX_LABELS = 8
}
