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
        assertTrue(LocalCommandPolicy.parse("/open example.com") is LocalCommand.Invalid)
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
    fun createsEncodedMapsSearchUrl() {
        val result = LocalCommandPolicy.parse("/maps Visakhapatnam railway station")
        assertTrue(result is LocalCommand.MapsSearch)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Visakhapatnam+railway+station",
            (result as LocalCommand.MapsSearch).url
        )
    }

    @Test
    fun dialCommandOnlyCreatesDialerUriAndNeverCalls() {
        assertEquals(
            LocalCommand.DialNumber("+919876543210", "tel:+919876543210"),
            LocalCommandPolicy.parse("/dial +91 98765-43210")
        )
        assertTrue(LocalCommandPolicy.parse("/dial 12") is LocalCommand.Invalid)
        assertTrue(LocalCommandPolicy.parse("/dial 123;456") is LocalCommand.Invalid)
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
