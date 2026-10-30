package com.projectcat.jarvis.tools

/**
 * Tool calls are descriptions only until a real Android implementation is registered.
 * Sensitive tools must request explicit user approval before execution.
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
