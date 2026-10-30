package com.projectcat.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionAuditCommandPolicyTest {
    @Test
    fun listsOnlyWithExactCommand() {
        assertEquals(ActionAuditCommand.ListRecent, ActionAuditCommandPolicy.parse("/action-log"))
        assertTrue(ActionAuditCommandPolicy.parse("/action-log latest") is ActionAuditCommand.Invalid)
        assertNull(ActionAuditCommandPolicy.parse("/action-logbook"))
    }

    @Test
    fun clearRequiresSeparateExactConfirmation() {
        assertEquals(
            ActionAuditCommand.RequestClearConfirmation,
            ActionAuditCommandPolicy.parse("/clear-action-log")
        )
        assertEquals(
            ActionAuditCommand.ClearConfirmed,
            ActionAuditCommandPolicy.parse("/clear-action-log confirm")
        )
        assertTrue(ActionAuditCommandPolicy.parse("/clear-action-log please") is ActionAuditCommand.Invalid)
    }

    @Test
    fun auditPolicyAllowsOnlyActionCategoriesAndOutcomes() {
        assertTrue(ActionAuditPolicy.isValid("open_url", ActionAuditPolicy.HANDED_OFF))
        assertTrue(ActionAuditPolicy.isValid("dial_number", ActionAuditPolicy.CANCELLED))
        assertFalse(ActionAuditPolicy.isValid("https://example.com/private", ActionAuditPolicy.HANDED_OFF))
        assertFalse(ActionAuditPolicy.isValid("+919876543210", ActionAuditPolicy.FAILED))
        assertFalse(ActionAuditPolicy.isValid("open_url", "sent"))
    }
}
