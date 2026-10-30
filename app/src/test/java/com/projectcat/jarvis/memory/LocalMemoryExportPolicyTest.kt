package com.projectcat.jarvis.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMemoryExportPolicyTest {
    @Test
    fun exportsOnlyExplicitFactsAndNotes() {
        val text = LocalMemoryExportPolicy.format(
            listOf("Prefers Telugu learning resources"),
            listOf(LocalNote(7L, "Prepare for exams", 123L))
        )
        assertTrue(text.contains("Prefers Telugu learning resources"))
        assertTrue(text.contains("#7 — Prepare for exams"))
        assertTrue(text.contains("Chat history and action logs are excluded"))
    }

    @Test
    fun handlesEmptyDataAndBoundsOutput() {
        val empty = LocalMemoryExportPolicy.format(emptyList(), emptyList())
        assertTrue(empty.contains("MEMORY FACTS"))
        assertTrue(empty.contains("SAVED NOTES"))
        assertFalse(empty.contains("null"))
        val huge = LocalMemoryExportPolicy.format(
            listOf("x".repeat(300_000)),
            emptyList()
        )
        assertTrue(huge.length <= LocalMemoryExportPolicy.MAX_EXPORT_CHARS)
    }
}
