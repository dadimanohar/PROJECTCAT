package com.projectcat.jarvis.tools

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface LocalCommand {
    data object Help : LocalCommand
    data class OpenUrl(val url: String) : LocalCommand
    data class SearchWeb(val query: String, val url: String) : LocalCommand
    data class MapsSearch(val query: String, val url: String) : LocalCommand
    data class DialNumber(val number: String, val uri: String) : LocalCommand
    data class Invalid(val message: String) : LocalCommand
}

/**
 * Parses only commands explicitly typed by the user.
 *
 * This is deliberately separate from model output: an AI response cannot invoke
 * a local Android action by returning command-looking text.
 */
object LocalCommandPolicy {
    private const val MAX_URL_LENGTH = 2048
    private const val MAX_SEARCH_LENGTH = 300

    fun parse(input: String): LocalCommand? {
        val command = input.trim()
        if (command.isEmpty()) return null

        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator))
            .lowercase()
        val argument = if (separator < 0) "" else command.substring(separator).trim()

        return when (name) {
            "/help" -> if (argument.isEmpty()) LocalCommand.Help
                else LocalCommand.Invalid("/help does not accept an argument.")
            "/open" -> parseOpen(argument)
            "/search" -> parseSearch(argument)
            "/maps" -> parseMaps(argument)
            "/dial" -> parseDial(argument)
            else -> null
        }
    }

    private fun parseOpen(value: String): LocalCommand {
        if (value.isBlank()) {
            return LocalCommand.Invalid("Usage: /open https://example.com")
        }
        if (value.length > MAX_URL_LENGTH || value.any(Char::isWhitespace) || value.any(Char::isISOControl)) {
            return LocalCommand.Invalid("The URL is too long or contains invalid whitespace.")
        }

        val uri = runCatching { URI(value) }.getOrNull()
            ?: return LocalCommand.Invalid("That URL is invalid.")
        if (uri.scheme?.equals("https", ignoreCase = true) != true ||
            uri.host.isNullOrBlank() ||
            uri.userInfo != null ||
            uri.isOpaque ||
            (uri.port != -1 && uri.port !in 1..65535)
        ) {
            return LocalCommand.Invalid("Only valid HTTPS URLs without embedded credentials are allowed.")
        }
        return LocalCommand.OpenUrl(uri.toASCIIString())
    }

    private fun parseMaps(query: String): LocalCommand {
        if (query.isBlank()) {
            return LocalCommand.Invalid("Usage: /maps <place or address>")
        }
        if (query.length > MAX_SEARCH_LENGTH || query.any(Char::isISOControl)) {
            return LocalCommand.Invalid("Map search text must be 1–300 characters and cannot contain control characters.")
        }
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return LocalCommand.MapsSearch(
            query = query,
            url = "https://www.google.com/maps/search/?api=1&query=$encoded"
        )
    }

    private fun parseDial(number: String): LocalCommand {
        if (number.isBlank()) {
            return LocalCommand.Invalid("Usage: /dial <phone number>")
        }
        if (number.length > 25 || !number.matches(Regex("[0-9+().\\s-]+"))) {
            return LocalCommand.Invalid("Enter a phone number using digits, an optional leading +, spaces, parentheses, or hyphens.")
        }
        val normalized = number.replace(Regex("[().\\s-]"), "")
        if (!normalized.matches(Regex("\\+?[0-9]{3,15}"))) {
            return LocalCommand.Invalid("Phone number must contain 3–15 digits and may have one leading +.")
        }
        return LocalCommand.DialNumber(normalized, "tel:$normalized")
    }

    private fun parseSearch(query: String): LocalCommand {
        if (query.isBlank()) {
            return LocalCommand.Invalid("Usage: /search <what you want to find>")
        }
        if (query.length > MAX_SEARCH_LENGTH || query.any(Char::isISOControl)) {
            return LocalCommand.Invalid("Search text must be 1–300 characters and cannot contain control characters.")
        }
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return LocalCommand.SearchWeb(
            query = query,
            url = "https://www.google.com/search?q=$encoded"
        )
    }
}
