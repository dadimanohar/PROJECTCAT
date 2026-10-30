package com.projectcat.jarvis.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

data class LocalActionExecutionResult(
    val succeeded: Boolean,
    val message: String
)

class AndroidLocalActionExecutor(context: Context) {
    private val appContext = context.applicationContext

    fun execute(command: LocalCommand, confirmedByUser: Boolean): LocalActionExecutionResult {
        if (!confirmedByUser) {
            return LocalActionExecutionResult(false, "Action blocked: explicit user approval is required.")
        }

        val risk = when (command) {
            is LocalCommand.DialNumber -> RiskLevel.SENSITIVE
            is LocalCommand.OpenUrl,
            is LocalCommand.SearchWeb,
            is LocalCommand.MapsSearch -> RiskLevel.REVERSIBLE
            else -> return LocalActionExecutionResult(false, "This command is not an executable Android action.")
        }
        val request = ToolRequest(
            id = "local-" + command.javaClass.simpleName,
            name = command.javaClass.simpleName ?: "local_action",
            description = "User-requested Android handoff",
            risk = risk
        )
        val decision = ToolPolicy.evaluate(request, approvedByUser = confirmedByUser)
        if (!decision.canExecute) {
            return LocalActionExecutionResult(false, "Action blocked: approval policy did not permit execution.")
        }

        return try {
            when (command) {
                is LocalCommand.OpenUrl -> launchChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(command.url)),
                    "Open link with"
                )
                is LocalCommand.SearchWeb -> launchChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(command.url)),
                    "Search the web with"
                )
                is LocalCommand.MapsSearch -> launchChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(command.url)),
                    "Find this place with"
                )
                is LocalCommand.DialNumber -> {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse(command.uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                    LocalActionExecutionResult(
                        true,
                        "Opened the dialer with the number filled in. Review it and tap Call yourself."
                    )
                }
                else -> LocalActionExecutionResult(false, "Unsupported local Android action.")
            }
        } catch (error: Exception) {
            LocalActionExecutionResult(
                false,
                "Android could not open the requested action: " + (error.message ?: "No compatible app is available.")
            )
        }
    }

    private fun launchChooser(intent: Intent, title: String): LocalActionExecutionResult {
        val chooser = Intent.createChooser(intent, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
        return LocalActionExecutionResult(true, "Opened the Android app chooser.")
    }
}
