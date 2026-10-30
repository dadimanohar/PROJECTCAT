package com.projectcat.jarvis.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyModePolicyTest {
    @Test
    fun parsesStudyModeCommands() {
        assertEquals(StudyModeCommand.SetEnabled(true), StudyModePolicy.parse("/study on"))
        assertEquals(StudyModeCommand.SetEnabled(false), StudyModePolicy.parse("/study off"))
        assertEquals(StudyModeCommand.Status, StudyModePolicy.parse("/study"))
        assertEquals(StudyModeCommand.Status, StudyModePolicy.parse("/study status"))
    }

    @Test
    fun rejectsUnknownStudyModeOptions() {
        assertTrue(StudyModePolicy.parse("/study maybe") is StudyModeCommand.Invalid)
        assertNull(StudyModePolicy.parse("/studying on"))
        assertNull(StudyModePolicy.parse("teach me algebra"))
    }

    @Test
    fun studyInstructionRequiresSocraticGuidance() {
        assertTrue(StudyModePolicy.SYSTEM_INSTRUCTION.contains("Do not give the final answer immediately"))
        assertTrue(StudyModePolicy.SYSTEM_INSTRUCTION.contains("exactly one question at a time"))
        assertTrue(StudyModePolicy.SYSTEM_INSTRUCTION.contains("Wait for the learner's response"))
    }
}
