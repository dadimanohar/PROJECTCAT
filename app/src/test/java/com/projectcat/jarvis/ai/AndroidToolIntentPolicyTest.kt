package com.projectcat.jarvis.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidToolIntentPolicyTest {
    @Test
    fun validatesExplicitInstalledAppLaunch() {
        assertEquals(
            AndroidToolCommand.OpenApp("com.example.reader"),
            AndroidToolIntentPolicy.parse("/open-app com.example.reader")
        )
        assertTrue(AndroidToolIntentPolicy.parse("/open-app not-a-package") is AndroidToolCommand.Invalid)
    }

    @Test
    fun validatesUserInitiatedShareText() {
        assertEquals(
            AndroidToolCommand.ShareText("Hello", "Share from Jarvis"),
            AndroidToolIntentPolicy.parse("/share Hello")
        )
        assertTrue(AndroidToolIntentPolicy.parse("/share " + "x".repeat(2001)) is AndroidToolCommand.Invalid)
    }

    @Test
    fun validatesReminderDurationsAndTitles() {
        assertEquals(
            AndroidToolCommand.SetReminder("Study", 90),
            AndroidToolIntentPolicy.parse("/remind Study in 90 minutes")
        )
        assertEquals(
            AndroidToolCommand.SetReminder("Call home", 120),
            AndroidToolIntentPolicy.parse("/remind Call home in 2 hours")
        )
        assertTrue(AndroidToolIntentPolicy.parse("/remind Study in 0 minutes") is AndroidToolCommand.Invalid)
        assertTrue(AndroidToolIntentPolicy.parse("/remind Study in 99999 hours") is AndroidToolCommand.Invalid)
    }

    @Test
    fun validatesFilePickerMimeTypeAndIgnoresUnknownText() {
        assertEquals(
            AndroidToolCommand.OpenFilePicker("application/pdf"),
            AndroidToolIntentPolicy.parse("/pick-file application/pdf")
        )
        assertEquals(
            AndroidToolCommand.OpenFilePicker("*/*"),
            AndroidToolIntentPolicy.parse("/pick-file")
        )
        assertTrue(AndroidToolIntentPolicy.parse("/pick-file no-mime") is AndroidToolCommand.Invalid)
        assertNull(AndroidToolIntentPolicy.parse("open calculator"))
    }
}
