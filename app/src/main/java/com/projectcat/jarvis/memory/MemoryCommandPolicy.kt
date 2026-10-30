package com.projectcat.jarvis.memory

sealed interface MemoryCommand {
    data class Remember(val fact: String) : MemoryCommand
    data class Recall(val query: String) : MemoryCommand
    data class Forget(val fact: String) : MemoryCommand
    data object ListFacts : MemoryCommand
    data object RequestClearAllConfirmation : MemoryCommand
    data object ConfirmClearAll : MemoryCommand
    data class Invalid(val message: String) : MemoryCommand
}

/**
 * Parses only an exact memory command name. Prefix lookalikes such as
 * "/remembered" remain ordinary chat text instead of mutating local state.
 */
object MemoryCommandPolicy {
    fun parse(input: String): MemoryCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null

        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator))
            .lowercase()
        val argument = if (separator < 0) "" else command.substring(separator).trim()

        return when (name) {
            "/remember" -> if (argument.isBlank()) {
                MemoryCommand.Invalid("Usage: /remember <fact>")
            } else {
                MemoryCommand.Remember(argument)
            }
            "/recall" -> if (argument.isBlank()) {
                MemoryCommand.Invalid("Usage: /recall <keyword>")
            } else {
                MemoryCommand.Recall(argument)
            }
            "/forget" -> if (argument.isBlank()) {
                MemoryCommand.Invalid("Usage: /forget <exact saved fact>")
            } else {
                MemoryCommand.Forget(argument)
            }
            "/memories" -> if (argument.isBlank()) {
                MemoryCommand.ListFacts
            } else {
                MemoryCommand.Invalid("/memories does not accept an argument.")
            }
            "/forget-all" -> when (argument.lowercase()) {
                "" -> MemoryCommand.RequestClearAllConfirmation
                "confirm" -> MemoryCommand.ConfirmClearAll
                else -> MemoryCommand.Invalid("To clear every saved memory, type exactly: /forget-all confirm")
            }
            else -> null
        }
    }
}
