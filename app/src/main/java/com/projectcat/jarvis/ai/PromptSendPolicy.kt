package com.projectcat.jarvis.ai

import com.projectcat.jarvis.memory.ActionAuditCommandPolicy
import com.projectcat.jarvis.memory.MemoryCommandPolicy
import com.projectcat.jarvis.tools.LocalCommandPolicy

/**
 * Local commands must remain usable even when the selected offline model is not
 * installed or ready. Regular chat still requires the selected inference engine.
 */
object PromptSendPolicy {
    fun canSend(
        prompt: String,
        isSending: Boolean,
        offlineSelected: Boolean,
        offlineReady: Boolean
    ): Boolean {
        if (isSending || prompt.isBlank()) return false
        if (!offlineSelected || offlineReady) return true

        val command = prompt.trim()
        return LocalCommandPolicy.parse(command) != null ||
            MemoryCommandPolicy.parse(command) != null ||
            ActionAuditCommandPolicy.parse(command) != null ||
            AgentRequestPolicy.parse(command) != null
    }
}
