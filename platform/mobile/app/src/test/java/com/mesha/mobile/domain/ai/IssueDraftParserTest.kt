package com.mesha.mobile.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the validate-and-repair contract for raw local-model output. These cases
 * mirror the real deviations Gemma-class models produce (fences, prose, string-instead-of
 * -array, bad priority, missing fields).
 */
class IssueDraftParserTest {

    @Test
    fun parsesCleanJson() {
        val raw = """
            {"title":"Add uninstall handling","description":"Handle GitHub App uninstall webhook",
             "acceptanceCriteria":["Webhook received","Installation removed"],
             "priority":"HIGH","labels":["backend","github"]}
        """.trimIndent()

        val draft = IssueDraftParser.parse(raw)

        assertEquals("Add uninstall handling", draft.title)
        assertEquals("Handle GitHub App uninstall webhook", draft.description)
        assertEquals(2, draft.acceptanceCriteria.size)
        assertEquals(IssuePriority.HIGH, draft.priority)
        assertEquals(listOf("backend", "github"), draft.labels)
    }

    @Test
    fun stripsMarkdownFencesAndProse() {
        val raw = """
            Sure! Here is your issue:
            ```json
            {"title":"Fix login","description":"Users cannot log in","priority":"urgent"}
            ```
            Hope this helps.
        """.trimIndent()

        val draft = IssueDraftParser.parse(raw)

        assertEquals("Fix login", draft.title)
        assertEquals(IssuePriority.URGENT, draft.priority)
    }

    @Test
    fun repairsAcceptanceCriteriaAsNewlineString() {
        val raw = """
            {"title":"T","description":"D","acceptanceCriteria":"- First\n- Second\n- Third"}
        """.trimIndent()

        val draft = IssueDraftParser.parse(raw)

        assertEquals(listOf("First", "Second", "Third"), draft.acceptanceCriteria)
    }

    @Test
    fun unknownPriorityDefaultsToMedium() {
        val raw = """{"title":"T","description":"D","priority":"super-important"}"""
        assertEquals(IssuePriority.MEDIUM, IssueDraftParser.parse(raw).priority)
    }

    @Test
    fun missingPriorityDefaultsToMedium() {
        val raw = """{"title":"T","description":"D"}"""
        assertEquals(IssuePriority.MEDIUM, IssueDraftParser.parse(raw).priority)
    }

    @Test
    fun deriveTitleFromPromptWhenMissing() {
        val raw = """{"description":"Some details about the work."}"""
        val draft = IssueDraftParser.parse(raw, fallbackTitleSource = "Need offline mode. It should sync.")
        assertEquals("Need offline mode", draft.title)
    }

    @Test
    fun ignoresUnknownKeysAndExtraText() {
        val raw = """{"title":"T","description":"D","foo":"bar","labels":["x"],"nested":{"a":1}}"""
        val draft = IssueDraftParser.parse(raw)
        assertEquals("T", draft.title)
        assertEquals(listOf("x"), draft.labels)
    }

    @Test
    fun dedupesAndTrimsLabelsAndCriteria() {
        val raw = """
            {"title":"T","description":"D",
             "acceptanceCriteria":["  a  ","a","b"],
             "labels":["#ui"," ui ","api"]}
        """.trimIndent()

        val draft = IssueDraftParser.parse(raw)
        assertEquals(listOf("a", "b"), draft.acceptanceCriteria)
        assertEquals(listOf("ui", "api"), draft.labels)
    }

    @Test
    fun picksIssueJsonWhenMultipleFencedBlocksPresent() {
        val raw = """
            First, run this:
            ```sh
            { "cmd": "do something" }
            ```
            Then here is the issue:
            ```json
            {"title":"Real issue","description":"the actual draft","priority":"LOW"}
            ```
        """.trimIndent()

        val draft = IssueDraftParser.parse(raw)
        assertEquals("Real issue", draft.title)
        assertEquals(IssuePriority.LOW, draft.priority)
    }

    @Test
    fun throwsWhenNoJsonPresent() {
        assertThrows(LocalAiException.InvalidOutput::class.java) {
            IssueDraftParser.parse("I cannot help with that.")
        }
    }

    @Test
    fun throwsWhenJsonHasNoTitleOrDescription() {
        assertThrows(LocalAiException.InvalidOutput::class.java) {
            IssueDraftParser.parse("""{"priority":"HIGH"}""")
        }
    }

    @Test
    fun extractsBalancedBracesIgnoringBracesInStrings() {
        val raw = """prefix {"title":"a } b","description":"d"} suffix"""
        val draft = IssueDraftParser.parse(raw)
        assertEquals("a } b", draft.title)
    }

    @Test
    fun titleIsTruncatedToMaxLength() {
        val longTitle = "x".repeat(300)
        val raw = """{"title":"$longTitle","description":"d"}"""
        val draft = IssueDraftParser.parse(raw)
        assertTrue(draft.title.length <= 140)
    }
}
