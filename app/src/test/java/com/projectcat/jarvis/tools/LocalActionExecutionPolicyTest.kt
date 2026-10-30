package com.projectcat.jarvis.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalActionExecutionPolicyTest {
    @Test
    fun browserAndMapsActionsAreReversible() {
        val decision = ToolPolicy.evaluate(
            ToolRequest("open", "open_url", "Open a web link", RiskLevel.REVERSIBLE),
            approvedByUser = false
        )
        assertTrue(decision.canExecute)
        assertFalse(decision.approval == ApprovalState.PENDING)
    }

    @Test
    fun dialerIsSensitiveAndRequiresUserApproval() {
        val pending = ToolPolicy.evaluate(
            ToolRequest("dial", "dial_number", "Open dialer", RiskLevel.SENSITIVE),
            approvedByUser = false
        )
        assertFalse(pending.canExecute)
        assertTrue(pending.approval == ApprovalState.PENDING)
        val approved = ToolPolicy.evaluate(pending.request, approvedByUser = true)
        assertTrue(approved.canExecute)
    }
}
