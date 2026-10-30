package com.projectcat.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCommandPolicyTest {
    @Test
    fun parsesLocalNoteCreationAndListing() {
        assertEquals(NoteCommand.Add("Buy milk"), NoteCommandPolicy.parse("/note Buy milk"))
        assertEquals(NoteCommand.List, NoteCommandPolicy.parse("/notes"))
    }

    @Test
    fun requiresExplicitConfirmationBeforeDeletingNote() {
        assertEquals(
            NoteCommand.RequestDeleteConfirmation(42L),
            NoteCommandPolicy.parse("/delete-note 42")
        )
        assertEquals(
            NoteCommand.ConfirmDelete(42L),
            NoteCommandPolicy.parse("/delete-note 42 confirm")
        )
        assertTrue(NoteCommandPolicy.parse("/delete-note 42 now") is NoteCommand.Invalid)
    }

    @Test
    fun rejectsInvalidNoteIdsAndOversizedNotes() {
        assertTrue(NoteCommandPolicy.parse("/delete-note 0") is NoteCommand.Invalid)
        assertTrue(NoteCommandPolicy.parse("/delete-note nope") is NoteCommand.Invalid)
        assertTrue(NoteCommandPolicy.parse("/note " + "x".repeat(1001)) is NoteCommand.Invalid)
    }

    @Test
    fun rejectsUnexpectedArgumentsAndIgnoresOrdinaryChat() {
        assertTrue(NoteCommandPolicy.parse("/notes extra") is NoteCommand.Invalid)
        assertNull(NoteCommandPolicy.parse("remember to buy milk"))
        assertNull(NoteCommandPolicy.parse("/notebook text"))
    }
}
