package com.mesha.mobile.domain.ai.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Turns the raw text an on-device LLM emits on an agent turn into a structured [AgentAction].
 *
 * On-device models are small and inconsistent, so this parser is deliberately forgiving. It
 * accepts any of the shapes they tend to produce for a tool call:
 *
 * ```
 * {"tool":"add_comment","arguments":{"ticket":"MES-1","body":"done"}}
 * {"action":"add_comment","args":{"ticket":"MES-1","body":"done"}}
 * {"tool":"add_comment","ticket":"MES-1","body":"done"}      // flat: siblings are the args
 * ```
 *
 * and for a final answer:
 *
 * ```
 * {"final":"Here are your open tickets: …"}
 * {"tool":"final","answer":"…"}
 * plain prose with no JSON at all                            // treated as the final answer
 * ```
 *
 * Pure and dependency-free so it can be unit-tested without an Android runtime. The
 * balanced-brace extraction mirrors [com.mesha.mobile.domain.ai.IssueDraftParser].
 */
object AgentActionParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val TOOL_KEYS = listOf("tool", "action", "tool_name", "toolName")
    private val ARG_KEYS = listOf("arguments", "args", "input", "parameters", "params")
    private val FINAL_KEYS = listOf("final", "answer", "response", "reply", "message", "final_answer", "finalAnswer")
    private val FINAL_TOOL_NAMES = setOf("final", "finish", "done", "respond", "answer", "final_answer", "none")

    /** Keys that are protocol scaffolding rather than tool arguments when args are given flat. */
    private val NON_ARG_KEYS = (TOOL_KEYS + FINAL_KEYS + listOf("thought", "reasoning", "reason", "observation")).toSet()

    /**
     * Parse [raw] into an [AgentAction]. Never throws: unrecognizable output falls back to a
     * [AgentAction.Final] carrying the trimmed text so the loop always terminates gracefully.
     */
    fun parse(raw: String): AgentAction {
        val jsonText = extractJsonObject(raw)
            ?: return AgentAction.Final(raw.trim())

        val root: JsonObject = try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (e: Exception) {
            return AgentAction.Final(raw.trim())
        }

        val toolName = root.firstString(TOOL_KEYS)?.trim()?.lowercase()

        // Explicit final-answer field always wins (covers {"tool":"final","answer":"…"} too).
        root.firstString(FINAL_KEYS)?.let { finalText ->
            if (toolName == null || toolName in FINAL_TOOL_NAMES) {
                return AgentAction.Final(finalText.trim())
            }
        }

        if (toolName.isNullOrBlank() || toolName in FINAL_TOOL_NAMES) {
            // No actionable tool. If there's a nested-args answer, surface it; else the raw text.
            val nested = root.argsObject()?.firstString(FINAL_KEYS)
            return AgentAction.Final((nested ?: raw).trim())
        }

        val arguments = root.argsObject() ?: root.flatArgs()
        return AgentAction.ToolCall(tool = toolName, arguments = arguments)
    }

    private fun JsonObject.firstString(keys: List<String>): String? {
        for (key in keys) {
            val v = (this[key] as? JsonPrimitive)?.contentOrNull
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /** The explicit arguments object under one of [ARG_KEYS], if present and an object. */
    private fun JsonObject.argsObject(): JsonObject? {
        for (key in ARG_KEYS) {
            val v = this[key]
            if (v is JsonObject) return v
        }
        return null
    }

    /** Treat all non-scaffolding sibling keys as the tool arguments (the "flat" call shape). */
    private fun JsonObject.flatArgs(): JsonObject =
        JsonObject(filterKeys { it !in NON_ARG_KEYS })

    /**
     * Locate a JSON object inside arbitrary text, tolerant of ```json fences, leading/trailing
     * prose and multiple blocks. Prefers a balanced `{ … }` block that looks like an agent
     * action (mentions a tool/final key), else falls back to the first balanced block.
     */
    internal fun extractJsonObject(raw: String): String? {
        if (raw.isBlank()) return null

        var firstBalanced: String? = null
        var searchFrom = 0
        val markers = (TOOL_KEYS + FINAL_KEYS).map { "\"$it\"" }
        while (true) {
            val start = raw.indexOf('{', searchFrom)
            if (start < 0) break
            val end = matchingBraceEnd(raw, start)
            if (end < 0) break
            val candidate = raw.substring(start, end + 1)
            if (firstBalanced == null) firstBalanced = candidate
            if (markers.any { candidate.contains(it) }) return candidate
            searchFrom = end + 1
        }
        return firstBalanced
    }

    /** Index of the `}` that closes the `{` at [start], or -1 if unbalanced. */
    private fun matchingBraceEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
