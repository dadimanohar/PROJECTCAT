package com.projectcat.jarvis.memory

sealed interface ActionAuditCommand {
    data object ListRecent : ActionAuditCommand
    data object RequestClearConfirmation : ActionAuditCommand
    data object ClearConfirmed : ActionAuditCommand
    data class Invalid(val message: String) : ActionAuditCommand
}

object ActionAuditCommandPolicy {
    fun parse(input: String): ActionAuditCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null
        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator)).lowercase()
        val argument = if (separator < 0) "" else command.substring(separator).trim()

        return when (name) {
            "/action-log" -> if (argument.isBlank()) {
                ActionAuditCommand.ListRecent
            } else {
                ActionAuditCommand.Invalid("/action-log does not accept an argument.")
            }
            "/clear-action-log" -> when (argument.lowercase()) {
                "" -> ActionAuditCommand.RequestClearConfirmation
                "confirm" -> ActionAuditCommand.ClearConfirmed
                else -> ActionAuditCommand.Invalid("To clear the action log, type exactly: /clear-action-log confirm")
            }
            else -> null
        }
    }
}
