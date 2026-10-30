package com.projectcat.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryPolicyTest {
    @Test
    fun retainsOnlyNewestThreeHundredMessages() {
        val entries = (1..350).map { ChatEntry("user", "message-$it") }

        val retained = ChatHistoryPolicy.retain(entries)

        assertEquals(300, retained.size)
        assertEquals("message-51", retained.first().text)
        assertEquals("message-350", retained.last().text)
    }

    @Test
    fun removesBlankEntries() {
        val entries = listOf(
            ChatEntry("user", "hello"),
            ChatEntry("", "invalid role"),
            ChatEntry("assistant", "  "),
            ChatEntry("assistant", "reply")
        )

        val retained = ChatHistoryPolicy.retain(entries)

        assertEquals(2, retained.size)
        assertEquals(listOf("hello", "reply"), retained.map { it.text })
    }

    @Test
    fun emptyHistoryRemainsEmpty() {
        assertTrue(ChatHistoryPolicy.retain(emptyList()).isEmpty())
    }
}
