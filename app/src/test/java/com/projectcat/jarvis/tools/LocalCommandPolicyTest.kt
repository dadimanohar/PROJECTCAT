package com.projectcat.jarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommandPolicyTest {
    @Test
    fun parsesHelpOnlyWhenNoArgumentIsSupplied() {
        assertEquals(LocalCommand.Help, LocalCommandPolicy.parse("/HELP"))
        assertTrue(LocalCommandPolicy.parse("/help now") is LocalCommand.Invalid)
    }

    @Test
    fun acceptsHttpsLinkAndNormalizesIt() {
        assertEquals(
            LocalCommand.OpenUrl("https://example.com/path"),
            LocalCommandPolicy.parse("/open https://example.com/path")
        )
    }

    @Test
    fun rejectsInsecureOrCredentialBearingLinks() {
        assertTrue(LocalCommandPolicy.parse("/open http://example.com") is LocalCommand.Invalid)
        assertTrue(LocalCommandPolicy.parse("/open javascript:alert(1)") is LocalCommand.Invalid)
        assertTrue(
            LocalCommandPolicy.parse("/open https://user:pass@example.com") is LocalCommand.Invalid
        )
        assertTrue(LocalCommandPolicy.parse("/open https:///missing-host") is LocalCommand.Invalid)
    }

    @Test
    fun createsEncodedGoogleSearchUrl() {
        val result = LocalCommandPolicy.parse("/search Jarvis + Android tools")
        assertTrue(result is LocalCommand.SearchWeb)
        assertEquals(
            "https://www.google.com/search?q=Jarvis+%2B+Android+tools",
            (result as LocalCommand.SearchWeb).url
        )
    }

    @Test
    fun rejectsEmptyAndOverlongSearchQueries() {
        assertTrue(LocalCommandPolicy.parse("/search   ") is LocalCommand.Invalid)
        assertTrue(LocalCommandPolicy.parse("/search " + "x".repeat(301)) is LocalCommand.Invalid)
    }

    @Test
    fun ignoresUnknownCommandsAndOrdinaryChat() {
        assertNull(LocalCommandPolicy.parse("hello Jarvis"))
        assertNull(LocalCommandPolicy.parse("/delete-everything"))
        assertNull(LocalCommandPolicy.parse("/openly https://example.com"))
    }
}
