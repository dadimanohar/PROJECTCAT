package com.projectcat.jarvis.ai

import com.projectcat.jarvis.tools.LocalCommand
import com.projectcat.jarvis.tools.LocalCommandPolicy

sealed interface AgentRequest {
    data class Task(val goal: String) : AgentRequest
    data class Invalid(val message: String) : AgentRequest
}

object AgentRequestPolicy {
    const val MAX_GOAL_LENGTH = 500

    fun parse(input: String): AgentRequest? {
        val command = input.trim()
        if (command.isEmpty()) return null
        val separator = command.indexOfFirst(Char::isWhitespace)
        val name = (if (separator < 0) command else command.substring(0, separator)).lowercase()
        if (name != "/agent") return null
        val goal = if (separator < 0) "" else command.substring(separator).trim()
        if (goal.isBlank()) return AgentRequest.Invalid("Usage: /agent <what you want Jarvis to do>")
        if (goal.length > MAX_GOAL_LENGTH || goal.any(Char::isISOControl)) {
            return AgentRequest.Invalid("Agent goals must be 1–500 characters and cannot contain control characters.")
        }
        return AgentRequest.Task(goal)
    }
}

data class AgentProposal(
    val goal: String,
    val action: LocalCommand,
    val reason: String
)

object AgentPlanParser {
    const val SYSTEM_INSTRUCTION = """
You are the Jarvis action planner. You may propose at most ONE action from this allowlist:
- open_url: open one valid HTTPS URL
- web_search: search the web for one query
- maps_search: search one place in Google Maps
- dial_number: open the Android dialer with one phone number prefilled (never place a call)

Return exactly three lines, with no Markdown and no extra text:
TOOL: one_allowlisted_tool_name
INPUT: one URL, search query, place, or phone number
REASON: one short sentence explaining why this action helps

Do not claim that you performed the action. Do not propose shell commands, messages, payments,
file deletion, app installation, reading private data, or any unlisted tool.
If no single allowlisted action safely helps, return exactly:
NO_ACTION
"""

    fun parse(goal: String, modelOutput: String): AgentProposal? {
        val cleanGoal = goal.trim()
        val output = modelOutput.trim()
        if (cleanGoal.isBlank() || cleanGoal.length > AgentRequestPolicy.MAX_GOAL_LENGTH) return null
        if (output.isBlank() || output.length > 3500 || output.equals("NO_ACTION", ignoreCase = true)) return null

        val lines = output.lines()
        if (lines.size != 3) return null
        val tool = valueAfter(lines[0], "TOOL")?.lowercase() ?: return null
        val input = valueAfter(lines[1], "INPUT") ?: return null
        val reason = valueAfter(lines[2], "REASON") ?: return null
        if (reason.isBlank() || reason.length > 180 || reason.any(Char::isISOControl)) return null

        val action = when (tool) {
            "open_url" -> LocalCommandPolicy.parse("/open " + input)
                ?.takeIf { it is LocalCommand.OpenUrl }
            "web_search" -> LocalCommandPolicy.parse("/search " + input)
                ?.takeIf { it is LocalCommand.SearchWeb }
            "maps_search" -> LocalCommandPolicy.parse("/maps " + input)
                ?.takeIf { it is LocalCommand.MapsSearch }
            "dial_number" -> LocalCommandPolicy.parse("/dial " + input)
                ?.takeIf { it is LocalCommand.DialNumber }
            else -> null
        } ?: return null

        return AgentProposal(cleanGoal, action, reason)
    }

    fun describe(action: LocalCommand): String = when (action) {
        is LocalCommand.OpenUrl -> "Open secure link " + action.url
        is LocalCommand.SearchWeb -> "Search the web for " + action.query
        is LocalCommand.MapsSearch -> "Find " + action.query + " in Maps"
        is LocalCommand.DialNumber -> "Open the dialer for " + action.number
        else -> "Unsupported action"
    }

    private fun valueAfter(line: String, label: String): String? {
        val prefix = "$label:"
        if (!line.startsWith(prefix, ignoreCase = true)) return null
        return line.substring(prefix.length).trim().takeIf { it.isNotBlank() }
    }
}
