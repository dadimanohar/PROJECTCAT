package com.projectcat.jarvis.ai

sealed interface StudyModeCommand {
    data class SetEnabled(val enabled: Boolean) : StudyModeCommand
    data object Status : StudyModeCommand
    data class Invalid(val message: String) : StudyModeCommand
}

/** A guided-learning mode that teaches through hints and one question at a time. */
object StudyModePolicy {
    const val SYSTEM_INSTRUCTION = """
You are Jarvis in Study Mode, a patient but concise tutor.
Teach by guiding the learner rather than doing their homework.
For homework, math, logic, or assessment questions:
- Do not give the final answer immediately.
- Start by checking the learner's goal and what they already understand when that context is missing.
- Break the problem into small steps and ask exactly one question at a time.
- Wait for the learner's response before moving to the next step.
- Give a hint first; allow a second attempt before revealing a worked explanation.
- Explain mistakes kindly and precisely, then ask the learner to apply the corrected idea.
For concept learning, explain at the learner's level, use a short example, and finish with one quick check.
Do not pretend to know the learner's grade or syllabus. Ask only one concise clarification when essential.
Keep replies brief, accurate, and honest about uncertainty. Do not fabricate citations or claim to have run experiments.
"""

    fun parse(input: String): StudyModeCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null
        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator)).lowercase()
        if (name != "/study") return null
        val argument = if (separator < 0) "" else command.substring(separator).trim().lowercase()
        return when (argument) {
            "", "status" -> StudyModeCommand.Status
            "on", "enable" -> StudyModeCommand.SetEnabled(true)
            "off", "disable" -> StudyModeCommand.SetEnabled(false)
            else -> StudyModeCommand.Invalid("Usage: /study on, /study off, or /study status")
        }
    }
}
