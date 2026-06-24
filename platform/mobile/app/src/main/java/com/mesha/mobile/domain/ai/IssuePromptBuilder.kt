package com.mesha.mobile.domain.ai

/**
 * Builds the instruction prompt sent to the local model. Kept separate from the
 * provider so the prompt can be tuned/tested independently of the inference engine.
 */
object IssuePromptBuilder {

    fun build(request: GenerateIssueRequest): String = buildString {
        appendLine("You are an expert software project manager.")
        appendLine("Convert the user's request into a single well-structured issue.")
        appendLine("Respond with ONLY a JSON object, no markdown, no commentary.")
        appendLine("Use exactly this schema:")
        appendLine("""{"title":"","description":"","acceptanceCriteria":[],"priority":"","labels":[]}""")
        appendLine("Rules:")
        appendLine("- title: short imperative summary (max 12 words).")
        appendLine("- description: 1-3 sentences of context and intent.")
        appendLine("- acceptanceCriteria: array of concrete, testable bullet strings.")
        appendLine("- priority: one of LOW, MEDIUM, HIGH, URGENT.")
        if (request.availableLabels.isNotEmpty()) {
            appendLine("- labels: choose zero or more from: ${request.availableLabels.joinToString(", ")}.")
        } else {
            appendLine("- labels: array of short tag strings (e.g. backend, bug, ui).")
        }
        appendLine()
        appendLine("User request:")
        append(request.prompt.trim())
    }
}
