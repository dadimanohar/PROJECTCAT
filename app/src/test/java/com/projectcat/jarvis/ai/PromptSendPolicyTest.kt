package com.projectcat.jarvis.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSendPolicyTest {
    @Test
    fun rejectsBlankPromptsAndSendingState() {
        assertFalse(PromptSendPolicy.canSend("   ", false, false, false))
        assertFalse(PromptSendPolicy.canSend("hello", true, false, false))
    }

    @Test
    fun onlineChatDoesNotRequireOfflineModel() {
        assertTrue(PromptSendPolicy.canSend("hello Jarvis", false, false, false))
    }

    @Test
    fun offlineChatRequiresReadyOfflineModel() {
        assertFalse(PromptSendPolicy.canSend("hello Jarvis", false, true, false))
        assertTrue(PromptSendPolicy.canSend("hello Jarvis", false, true, true))
    }

    @Test
    fun offlineLocalCommandsRemainAvailableWithoutModel() {
        assertTrue(PromptSendPolicy.canSend("/help", false, true, false))
        assertTrue(PromptSendPolicy.canSend("/remember I prefer Telugu", false, true, false))
        assertTrue(PromptSendPolicy.canSend("/open https://example.com", false, true, false))
        assertTrue(PromptSendPolicy.canSend("/action-log", false, true, false))
        assertTrue(PromptSendPolicy.canSend("/agent find the official site", false, true, false))
    }

    @Test
    fun unknownSlashTextStillRequiresOfflineModel() {
        assertFalse(PromptSendPolicy.canSend("/make-me-admin", false, true, false))
    }
}
