package com.projectcat.jarvis.tools

/**
 * Risk and approval policy used by the typed Android action executor.
 * This policy does not execute tools. The current executor only supports a narrow,
 * allowlisted set of browser, maps, and dialer handoffs.
 */

enum class RiskLevel { READ_ONLY, REVERSIBLE, SENSITIVE, DESTRUCTIVE }
enum class ApprovalState { NOT_REQUIRED, PENDING, APPROVED, REJECTED }

data class ToolRequest(
    val id: String,
    val name: String,
    val description: String,
    val risk: RiskLevel
)

data class ToolDecision(
    val request: ToolRequest,
    val approval: ApprovalState,
    val canExecute: Boolean
)

object ToolPolicy {
    fun evaluate(request: ToolRequest, approvedByUser: Boolean = false): ToolDecision {
        val requiresApproval = request.risk == RiskLevel.SENSITIVE ||
            request.risk == RiskLevel.DESTRUCTIVE
        val state = when {
            !requiresApproval -> ApprovalState.NOT_REQUIRED
            approvedByUser -> ApprovalState.APPROVED
            else -> ApprovalState.PENDING
        }
        return ToolDecision(
            request = request,
            approval = state,
            canExecute = !requiresApproval || approvedByUser
        )
    }
}
