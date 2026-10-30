package com.projectcat.jarvis.tools

import com.projectcat.jarvis.ai.ReminderCommand
import com.projectcat.jarvis.ai.ReminderCommandPolicy

sealed interface AndroidIntentCommand {
    data class OpenInstalledApp(val packageName: String) : AndroidIntentCommand
    data class ShareText(val text: String) : AndroidIntentCommand
    data class SetTimer(val title: String, val minutes: Int) : AndroidIntentCommand
    data class PickFile(val mimeType: String) : AndroidIntentCommand
    data class Invalid(val message: String) : AndroidIntentCommand
}

/** Parses only explicit user-entered requests; model-generated text is never passed here. */
object AndroidIntentCommandPolicy {
    const val MAX_SHARE_LENGTH = 2_000
    private val packagePattern = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")
    private val mimePattern = Regex("^(?:[A-Za-z0-9!#private val mimePattern = Regex("^[A-Za-z0-9!#$&^_.+-]+/(?:[A-Za-z0-9!#$&^_.+-]+|\\*)$")^_.+-]+|\\*)/(?:[A-Za-z0-9!#private val mimePattern = Regex("^[A-Za-z0-9!#$&^_.+-]+/(?:[A-Za-z0-9!#$&^_.+-]+|\\*)$")^_.+-]+|\\*)$")

    fun parse(input: String): AndroidIntentCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null
        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator)).lowercase()
        val argument = if (separator < 0) "" else command.substring(separator).trim()

        return when (name) {
            "/open-app" -> if (packagePattern.matches(argument)) {
                AndroidIntentCommand.OpenInstalledApp(argument)
            } else {
                AndroidIntentCommand.Invalid("Usage: /open-app com.example.app — the app opens only after you approve its package name.")
            }
            "/share" -> if (argument.isNotBlank() &&
                argument.length <= MAX_SHARE_LENGTH &&
                argument.none(Char::isISOControl)
            ) {
                AndroidIntentCommand.ShareText(argument)
            } else {
                AndroidIntentCommand.Invalid("Share text must be 1–2,000 characters and cannot contain control characters.")
            }
            "/remind" -> when (val reminder = ReminderCommandPolicy.parse(command)) {
                is ReminderCommand.Schedule -> AndroidIntentCommand.SetTimer(
                    reminder.title, reminder.minutesFromNow
                )
                is ReminderCommand.Invalid -> AndroidIntentCommand.Invalid(reminder.message)
                null -> AndroidIntentCommand.Invalid("Usage: /remind <title> in <number> minutes or hours.")
            }
            "/pick-file" -> {
                val mime = argument.ifBlank { "*/*" }
                if (mimePattern.matches(mime)) AndroidIntentCommand.PickFile(mime)
                else AndroidIntentCommand.Invalid("Use a valid MIME type such as */*, image/*, or application/pdf.")
            }
            else -> null
        }
    }
}
