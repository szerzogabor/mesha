package com.mesha.mobile.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptBuilderTest {

    @Test
    fun singleUserMessage_producesOpenModelTurn() {
        val history = listOf(LocalChatMessage(LocalChatMessage.Role.USER, "Hello"))
        val prompt = ChatPromptBuilder.build(history)

        assertTrue(prompt.contains("<start_of_turn>user\nHello"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun multiTurnHistory_interleavesUserAndAssistantTurns() {
        val history = listOf(
            LocalChatMessage(LocalChatMessage.Role.USER, "Hi"),
            LocalChatMessage(LocalChatMessage.Role.ASSISTANT, "Hello! How can I help?"),
            LocalChatMessage(LocalChatMessage.Role.USER, "Tell me a joke"),
        )
        val prompt = ChatPromptBuilder.build(history)

        val userStart = "<start_of_turn>user\n"
        val modelStart = "<start_of_turn>model\n"
        val turnEnd = "<end_of_turn>\n"

        assertTrue(prompt.contains("${userStart}Hi\n${turnEnd}"))
        assertTrue(prompt.contains("${modelStart}Hello! How can I help?\n${turnEnd}"))
        assertTrue(prompt.contains("${userStart}Tell me a joke\n${turnEnd}"))
        assertTrue(prompt.endsWith(modelStart))
    }

    @Test
    fun trailingWhitespace_isTrimmedFromMessages() {
        val history = listOf(LocalChatMessage(LocalChatMessage.Role.USER, "  padded  "))
        val prompt = ChatPromptBuilder.build(history)

        assertTrue(prompt.contains("<start_of_turn>user\npadded\n"))
    }

    @Test
    fun emptyHistory_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatPromptBuilder.build(emptyList())
        }
    }

    @Test
    fun historyEndingWithAssistantMessage_throwsIllegalArgumentException() {
        val history = listOf(
            LocalChatMessage(LocalChatMessage.Role.USER, "Hi"),
            LocalChatMessage(LocalChatMessage.Role.ASSISTANT, "Hello!"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ChatPromptBuilder.build(history)
        }
    }

    @Test
    fun promptAlwaysEndsWithOpenModelTurn() {
        repeat(3) { count ->
            val history = buildList {
                repeat(count + 1) { i ->
                    add(LocalChatMessage(LocalChatMessage.Role.USER, "Q${i + 1}"))
                    if (i < count) add(LocalChatMessage(LocalChatMessage.Role.ASSISTANT, "A${i + 1}"))
                }
            }
            val prompt = ChatPromptBuilder.build(history)
            assertTrue("Prompt must end with open model turn", prompt.endsWith("<start_of_turn>model\n"))
        }
    }

    @Test
    fun turnOrderIsPreservedInOutput() {
        val history = listOf(
            LocalChatMessage(LocalChatMessage.Role.USER, "first"),
            LocalChatMessage(LocalChatMessage.Role.ASSISTANT, "second"),
            LocalChatMessage(LocalChatMessage.Role.USER, "third"),
        )
        val prompt = ChatPromptBuilder.build(history)

        val firstIdx = prompt.indexOf("first")
        val secondIdx = prompt.indexOf("second")
        val thirdIdx = prompt.indexOf("third")

        assertTrue(firstIdx < secondIdx)
        assertTrue(secondIdx < thirdIdx)
    }
}
