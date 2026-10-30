package com.projectcat.jarvis.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

data class LocalActionExecutionResult(
    val actionKey: String?,
    val succeeded: Boolean,
    val outcome: String,
    val message: String
)

class AndroidLocalActionExecutor(context: Context) {
    private val appContext = context.applicationContext

    fun actionKey(command: LocalCommand): String? = when (command) {
        is LocalCommand.OpenUrl -> "open_url"
        is LocalCommand.SearchWeb -> "web_search"
        is LocalCommand.MapsSearch -> "maps_search"
        is LocalCommand.DialNumber -> "dial_number"
        else -> null
    }

    fun execute(command: LocalCommand, confirmedByUser: Boolean): LocalActionExecutionResult {
        val key = actionKey(command)
            ?: return LocalActionExecutionResult(null, false, "failed", "This command is not an executable Android action.")
        if (!confirmedByUser) {
            return LocalActionExecutionResult(key, false, "failed", "Action blocked: explicit user approval is required.")
        }

        val risk = if (command is LocalCommand.DialNumber) RiskLevel.SENSITIVE else RiskLevel.REVERSIBLE
        val request = ToolRequest(
            id = "local-" + key,
            name = key,
            description = "User-requested Android handoff",
            risk = risk
        )
        val decision = ToolPolicy.evaluate(request, approvedByUser = confirmedByUser)
        if (!decision.canExecute) {
            return LocalActionExecutionResult(key, false, "failed", "Action blocked: approval policy did not permit execution.")
        }

        return try {
            when (command) {
                is LocalCommand.OpenUrl -> launchChooser(
                    key,
                    Intent(Intent.ACTION_VIEW, Uri.parse(command.url)),
                    "Open link with"
                )
                is LocalCommand.SearchWeb -> launchChooser(
                    key,
                    Intent(Intent.ACTION_VIEW, Uri.parse(command.url)),
                    "Search the web with"
                )
                is LocalCommand.MapsSearch -> launchChooser(
                    key,
                    Intent(Intent.ACTION_VIEW, Uri.parse(command.url)),
                    "Find this place with"
                )
                is LocalCommand.DialNumber -> {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse(command.uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                    LocalActionExecutionResult(
                        key,
                        true,
                        "handed_off",
                        "Opened the dialer with the number filled in. Review it and tap Call yourself."
                    )
                }
                else -> LocalActionExecutionResult(key, false, "failed", "Unsupported local Android action.")
            }
        } catch (error: Exception) {
            LocalActionExecutionResult(
                key,
                false,
                "failed",
                "Android could not open the requested action: " + (error.message ?: "No compatible app is available.")
            )
        }
    }

    private fun launchChooser(key: String, intent: Intent, title: String): LocalActionExecutionResult {
        val chooser = Intent.createChooser(intent, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
        return LocalActionExecutionResult(key, true, "handed_off", "Opened the Android app chooser.")
    }
}
