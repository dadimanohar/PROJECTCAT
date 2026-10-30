package com.projectcat.jarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {
    @Test fun readOnlyToolDoesNotNeedApproval() {
        val decision = ToolPolicy.evaluate(ToolRequest("1", "list_files", "List files", RiskLevel.READ_ONLY))
        assertEquals(ApprovalState.NOT_REQUIRED, decision.approval)
        assertTrue(decision.canExecute)
    }

    @Test fun sensitiveToolWaitsForApproval() {
        val decision = ToolPolicy.evaluate(ToolRequest("2", "send_message", "Send a message", RiskLevel.SENSITIVE))
        assertEquals(ApprovalState.PENDING, decision.approval)
        assertFalse(decision.canExecute)
    }

    @Test fun sensitiveToolCanProceedAfterApproval() {
        val decision = ToolPolicy.evaluate(
            ToolRequest("3", "delete_file", "Delete a file", RiskLevel.DESTRUCTIVE),
            approvedByUser = true
        )
        assertEquals(ApprovalState.APPROVED, decision.approval)
        assertTrue(decision.canExecute)
    }
}
