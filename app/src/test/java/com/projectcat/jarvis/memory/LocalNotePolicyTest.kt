package com.projectcat.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalNotePolicyTest {
    @Test
    fun normalizesWhitespaceAndValidatesNoteLength() {
        assertEquals("buy milk tomorrow", LocalNotePolicy.validate("  buy   milk\n tomorrow  "))
        assertNull(LocalNotePolicy.validate("   "))
        assertNull(LocalNotePolicy.validate("x".repeat(NoteCommandPolicy.MAX_NOTE_LENGTH + 1)))
    }
}
