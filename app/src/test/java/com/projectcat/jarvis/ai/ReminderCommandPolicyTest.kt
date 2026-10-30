package com.projectcat.jarvis.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCommandPolicyTest {
    @Test
    fun parsesReminderWithMinutes() {
        assertEquals(
            ReminderCommand.Schedule("Study for maths", 45),
            ReminderCommandPolicy.parse("/remind Study for maths in 45 minutes")
        )
    }

    @Test
    fun parsesReminderWithHours() {
        assertEquals(
            ReminderCommand.Schedule("Call home", 120),
            ReminderCommandPolicy.parse("/remind Call home in 2 hours")
        )
    }

    @Test
    fun rejectsMissingInvalidAndOutOfRangeDuration() {
        assertTrue(ReminderCommandPolicy.parse("/remind Study") is ReminderCommand.Invalid)
        assertTrue(ReminderCommandPolicy.parse("/remind Study in 0 minutes") is ReminderCommand.Invalid)
        assertTrue(ReminderCommandPolicy.parse("/remind Study in 99999 hours") is ReminderCommand.Invalid)
    }

    @Test
    fun ignoresOrdinaryChatAndUnknownCommands() {
        assertNull(ReminderCommandPolicy.parse("remind me to study in 5 minutes"))
        assertNull(ReminderCommandPolicy.parse("/reminders"))
    }
}
