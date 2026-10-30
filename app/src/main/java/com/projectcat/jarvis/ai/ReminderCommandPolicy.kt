package com.projectcat.jarvis.ai

sealed interface ReminderCommand {
    data class Schedule(val title: String, val minutesFromNow: Int) : ReminderCommand
    data class Invalid(val message: String) : ReminderCommand
}

/** Parses explicit user reminder commands. This is validation only; it does not schedule alarms. */
object ReminderCommandPolicy {
    const val MAX_TITLE_LENGTH = 100
    const val MAX_MINUTES_FROM_NOW = 60 * 24 * 30

    fun parse(input: String): ReminderCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null

        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator)).lowercase()
        if (name != "/remind") return null

        val argument = if (separator < 0) "" else command.substring(separator).trim()
        val match = Regex(
            "^(.+?)\\s+in\\s+(\\d{1,5})\\s*(minutes?|mins?|m|hours?|hrs?|h)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(argument) ?: return ReminderCommand.Invalid(
            "Usage: /remind <title> in <number> minutes or hours."
        )

        val title = match.groupValues[1].trim()
        val amount = match.groupValues[2].toIntOrNull()
            ?: return ReminderCommand.Invalid("Reminder duration is invalid.")
        val unit = match.groupValues[3].lowercase()
        val multiplier = if (unit.startsWith("h")) 60 else 1

        if (title.isBlank() || title.length > MAX_TITLE_LENGTH || title.any(Char::isISOControl)) {
            return ReminderCommand.Invalid("Reminder title must be 1–100 characters and cannot contain control characters.")
        }
        if (amount <= 0 || amount > MAX_MINUTES_FROM_NOW / multiplier) {
            return ReminderCommand.Invalid("Reminder duration must be between 1 minute and 30 days.")
        }

        return ReminderCommand.Schedule(title, amount * multiplier)
    }
}
