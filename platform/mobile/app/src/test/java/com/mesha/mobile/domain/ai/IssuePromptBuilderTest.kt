package com.mesha.mobile.domain.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class IssuePromptBuilderTest {

    @Test
    fun includesSchemaAndUserPrompt() {
        val prompt = IssuePromptBuilder.build(
            GenerateIssueRequest("Need GitHub App uninstall handling."),
        )
        assertTrue(prompt.contains("acceptanceCriteria"))
        assertTrue(prompt.contains("LOW, MEDIUM, HIGH, URGENT"))
        assertTrue(prompt.contains("Need GitHub App uninstall handling."))
    }

    @Test
    fun constrainsLabelsToAvailableWhenProvided() {
        val prompt = IssuePromptBuilder.build(
            GenerateIssueRequest("Fix bug", availableLabels = listOf("bug", "backend")),
        )
        assertTrue(prompt.contains("choose zero or more from: bug, backend"))
    }
}
