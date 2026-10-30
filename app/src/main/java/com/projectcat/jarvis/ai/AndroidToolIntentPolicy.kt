package com.projectcat.jarvis.ai

import java.net.URI

sealed interface AndroidToolCommand {
    data class OpenApp(val packageName: String) : AndroidToolCommand
    data class ShareText(val text: String, val title: String) : AndroidToolCommand
    data class SetReminder(val title: String, val minutesFromNow: Int) : AndroidToolCommand
    data class OpenFilePicker(val mimeType: String) : AndroidToolCommand
    data class Invalid(val message: String) : AndroidToolCommand
}

/**
 * Narrow Android-intent catalogue. This policy only parses explicit user commands;
 * it never invokes an intent. Opening installed apps and reminders require confirmation.
 */
object AndroidToolIntentPolicy {
    const val MAX_TEXT_LENGTH = 2000
    const val MAX_TITLE_LENGTH = 100
    const val MAX_REMINDER_MINUTES = 60 * 24 * 30

    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    fun parse(input: String): AndroidToolCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null
        val split = command.indexOfFirst(Char::isWhitespace)
        val name = (if (split < 0) command else command.substring(0, split)).lowercase()
        val args = if (split < 0) "" else command.substring(split).trim()
        return when (name) {
            "/open-app" -> parseOpenApp(args)
            "/share" -> parseShare(args)
            "/remind" -> parseReminder(args)
            "/pick-file" -> parseFilePicker(args)
            else -> null
        }
    }

    private fun parseOpenApp(value: String): AndroidToolCommand {
        if (!packagePattern.matches(value) || value.length > 200) {
            return AndroidToolCommand.Invalid("Usage: /open-app <installed Android package name>")
        }
        return AndroidToolCommand.OpenApp(value)
    }

    private fun parseShare(value: String): AndroidToolCommand {
        if (value.isBlank() || value.length > MAX_TEXT_LENGTH || value.any(Char::isISOControl)) {
            return AndroidToolCommand.Invalid("Usage: /share <text up to 2,000 characters>")
        }
        return AndroidToolCommand.ShareText(value, "Share from Jarvis")
    }

    private fun parseReminder(value: String): AndroidToolCommand {
        val match = Regex("^(.{1,100})\\s+in\\s+(\\d{1,5})\\s*(m|min|minutes|h|hours)$", RegexOption.IGNORE_CASE)
            .matchEntire(value) ?: return AndroidToolCommand.Invalid("Usage: /remind <title> in <number> minutes|hours")
        val title = match.groupValues[1].trim()
        val count = match.groupValues[2].toIntOrNull()
            ?: return AndroidToolCommand.Invalid("Reminder duration is invalid.")
        val unit = match.groupValues[3].lowercase()
        val minutes = if (unit == "h" || unit == "hours") count * 60 else count
        if (title.isBlank() || title.any(Char::isISOControl) || minutes !in 1..MAX_REMINDER_MINUTES) {
            return AndroidToolCommand.Invalid("Reminder duration must be between 1 minute and 30 days.")
        }
        return AndroidToolCommand.SetReminder(title, minutes)
    }

    private fun parseFilePicker(value: String): AndroidToolCommand {
        val mime = if (value.isBlank()) "*/*" else value
        if (mime.length > 100 || !Regex("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+*-]+$").matches(mime)) {
            return AndroidToolCommand.Invalid("Usage: /pick-file [MIME type, for example application/pdf]")
        }
        return AndroidToolCommand.OpenFilePicker(mime)
    }
}
