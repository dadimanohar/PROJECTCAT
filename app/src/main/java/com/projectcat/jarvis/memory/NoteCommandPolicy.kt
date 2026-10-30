package com.projectcat.jarvis.memory

sealed interface NoteCommand {
    data class Add(val text: String) : NoteCommand
    data object List : NoteCommand
    data class RequestDeleteConfirmation(val noteId: Long) : NoteCommand
    data class ConfirmDelete(val noteId: Long) : NoteCommand
    data class Invalid(val message: String) : NoteCommand
}

/** Parses explicit, local-only note commands. Note content is never sent to an AI provider. */
object NoteCommandPolicy {
    const val MAX_NOTE_LENGTH = 1000

    fun parse(input: String): NoteCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null

        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator)).lowercase()
        val argument = if (separator < 0) "" else command.substring(separator).trim()

        return when (name) {
            "/note" -> when {
                argument.isBlank() -> NoteCommand.Invalid("Usage: /note <text to save locally>")
                argument.length > MAX_NOTE_LENGTH || argument.any(Char::isISOControl) ->
                    NoteCommand.Invalid("Notes must be 1–1000 characters and cannot contain control characters.")
                else -> NoteCommand.Add(argument)
            }
            "/notes" -> if (argument.isBlank()) NoteCommand.List
                else NoteCommand.Invalid("/notes does not accept an argument.")
            "/delete-note" -> {
                val parts = argument.split(Regex("\\s+")).filter(String::isNotBlank)
                val id = parts.firstOrNull()?.toLongOrNull()
                when {
                    id == null || id <= 0L -> NoteCommand.Invalid("Usage: /delete-note <note id> [confirm]")
                    parts.size == 1 -> NoteCommand.RequestDeleteConfirmation(id)
                    parts.size == 2 && parts[1].equals("confirm", ignoreCase = true) ->
                        NoteCommand.ConfirmDelete(id)
                    else -> NoteCommand.Invalid("To delete a note, use /delete-note <id>, then /delete-note <id> confirm.")
                }
            }
            else -> null
        }
    }
}

object LocalNotePolicy {
    const val MAX_NOTES = 200

    fun validate(text: String): String? {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        return clean.takeIf { it.isNotBlank() && it.length <= NoteCommandPolicy.MAX_NOTE_LENGTH &&
            clean.none(Char::isISOControl) }
    }

    fun retainIds(idsNewestFirst: List<Long>): List<Long> =
        idsNewestFirst.filter { it > 0L }.distinct().take(MAX_NOTES)
}
