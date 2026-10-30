package com.projectcat.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryFactPolicyTest {
    @Test
    fun normalizesWhitespaceAndCaseForExactMatching() {
        assertEquals(
            "my favorite language is telugu",
            MemoryFactPolicy.normalize("  My   favorite language IS Telugu  ")
        )
    }

    @Test
    fun rejectsBlankMemoryFacts() {
        assertNull(MemoryFactPolicy.validate("   "))
    }

    @Test
    fun rejectsFactsLongerThanFiveHundredCharacters() {
        assertNull(MemoryFactPolicy.validate("x".repeat(501)))
    }

    @Test
    fun trimsAndCollapsesWhitespace() {
        assertEquals("I prefer concise answers", MemoryFactPolicy.validate("  I   prefer concise answers  "))
    }
}
