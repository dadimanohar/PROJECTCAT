package com.projectcat.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCommandPolicyTest {
    @Test
    fun parsesRememberRecallAndExactForgetCommands() {
        assertEquals(MemoryCommand.Remember("I prefer Telugu"), MemoryCommandPolicy.parse("/remember I prefer Telugu"))
        assertEquals(MemoryCommand.Recall("Telugu"), MemoryCommandPolicy.parse("/recall Telugu"))
        assertEquals(MemoryCommand.Forget("I prefer Telugu"), MemoryCommandPolicy.parse("/forget I prefer Telugu"))
    }

    @Test
    fun rejectsMissingArguments() {
        assertTrue(MemoryCommandPolicy.parse("/remember") is MemoryCommand.Invalid)
        assertTrue(MemoryCommandPolicy.parse("/recall   ") is MemoryCommand.Invalid)
        assertTrue(MemoryCommandPolicy.parse("/forget") is MemoryCommand.Invalid)
    }

    @Test
    fun listsSavedMemoriesOnlyWithoutArguments() {
        assertEquals(MemoryCommand.ListFacts, MemoryCommandPolicy.parse("/memories"))
        assertTrue(MemoryCommandPolicy.parse("/memories anything") is MemoryCommand.Invalid)
    }

    @Test
    fun clearingAllMemoryRequiresSeparateExactConfirmation() {
        assertEquals(
            MemoryCommand.RequestClearAllConfirmation,
            MemoryCommandPolicy.parse("/forget-all")
        )
        assertEquals(
            MemoryCommand.ConfirmClearAll,
            MemoryCommandPolicy.parse("/forget-all confirm")
        )
        assertTrue(MemoryCommandPolicy.parse("/forget-all please") is MemoryCommand.Invalid)
        assertTrue(MemoryCommandPolicy.parse("/forget-all confirm now") is MemoryCommand.Invalid)
    }

    @Test
    fun unknownLookalikeCommandsRemainOrdinaryChat() {
        assertNull(MemoryCommandPolicy.parse("/remembered this is not a command"))
        assertNull(MemoryCommandPolicy.parse("/recall-old"))
        assertNull(MemoryCommandPolicy.parse("hello Jarvis"))
    }
}
