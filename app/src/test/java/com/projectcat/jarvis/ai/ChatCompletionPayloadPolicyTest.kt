package com.projectcat.jarvis.ai

import com.projectcat.jarvis.memory.ChatEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionPayloadPolicyTest {
    @Test
    fun systemInstructionIsASeparateSystemRoleBeforeConversation() {
        val messages = ChatCompletionPayloadPolicy.messages(
            listOf(ChatEntry("You", "Explain fractions"), ChatEntry("Jarvis", "What do you know already?")),
            "Teach with hints and one question at a time."
        )
        assertEquals("system", messages[0].role)
        assertEquals("Teach with hints and one question at a time.", messages[0].content)
        assertEquals("user", messages[1].role)
        assertEquals("assistant", messages[2].role)
    }

    @Test
    fun mapsConversationRolesAndCapsHistoryAtTwentyFour() {
        val history = (1..30).map { ChatEntry(if (it % 2 == 0) "Jarvis" else "You", "message-$it") }
        val messages = ChatCompletionPayloadPolicy.messages(history)
        assertEquals(24, messages.size)
        assertEquals("message-7", messages.first().content)
        assertEquals("assistant", messages[1].role)
        assertEquals("message-30", messages.last().content)
    }

    @Test
    fun omitsBlankSystemInstruction() {
        val messages = ChatCompletionPayloadPolicy.messages(listOf(ChatEntry("You", "Hi")), "  ")
        assertEquals(1, messages.size)
        assertTrue(messages.none { it.role == "system" })
    }
}
