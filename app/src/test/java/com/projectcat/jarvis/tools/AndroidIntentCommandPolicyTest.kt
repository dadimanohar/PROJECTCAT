package com.projectcat.jarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidIntentCommandPolicyTest {
    @Test
    fun acceptsOnlyWellFormedExplicitPackageNames() {
        assertEquals(
            AndroidIntentCommand.OpenInstalledApp("com.example.calendar"),
            AndroidIntentCommandPolicy.parse("/open-app com.example.calendar")
        )
        assertTrue(AndroidIntentCommandPolicy.parse("/open-app com.example") is AndroidIntentCommand.OpenInstalledApp)
        assertTrue(AndroidIntentCommandPolicy.parse("/open-app com.example;rm -rf") is AndroidIntentCommand.Invalid)
        assertTrue(AndroidIntentCommandPolicy.parse("/open-app") is AndroidIntentCommand.Invalid)
    }

    @Test
    fun validatesShareTextLengthAndControlCharacters() {
        assertEquals(
            AndroidIntentCommand.ShareText("Hello from Jarvis"),
            AndroidIntentCommandPolicy.parse("/share Hello from Jarvis")
        )
        assertTrue(AndroidIntentCommandPolicy.parse("/share " + "x".repeat(2_001)) is AndroidIntentCommand.Invalid)
        assertTrue(AndroidIntentCommandPolicy.parse("/share bad\u0000text") is AndroidIntentCommand.Invalid)
    }

    @Test
    fun reminderCommandIsValidatedAndConvertedToTimerRequest() {
        assertEquals(
            AndroidIntentCommand.SetTimer("Study", 90),
            AndroidIntentCommandPolicy.parse("/remind Study in 90 minutes")
        )
        assertTrue(AndroidIntentCommandPolicy.parse("/remind Study in 0 minutes") is AndroidIntentCommand.Invalid)
    }

    @Test
    fun validatesMimeTypeAndDefaultsToAllFiles() {
        assertEquals(AndroidIntentCommand.PickFile("*/*"), AndroidIntentCommandPolicy.parse("/pick-file"))
        assertEquals(AndroidIntentCommand.PickFile("image/*"), AndroidIntentCommandPolicy.parse("/pick-file image/*"))
        assertEquals(AndroidIntentCommand.PickFile("application/pdf"), AndroidIntentCommandPolicy.parse("/pick-file application/pdf"))
        assertTrue(AndroidIntentCommandPolicy.parse("/pick-file image/") is AndroidIntentCommand.Invalid)
        assertTrue(AndroidIntentCommandPolicy.parse("/pick-file */pdf") is AndroidIntentCommand.Invalid)
    }

    @Test
    fun ignoresOrdinaryChatAndUnknownCommands() {
        assertNull(AndroidIntentCommandPolicy.parse("please share this"))
        assertNull(AndroidIntentCommandPolicy.parse("/shareholder text"))
    }
}
