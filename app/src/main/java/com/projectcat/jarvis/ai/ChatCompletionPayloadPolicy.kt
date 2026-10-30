package com.projectcat.jarvis.ai

import com.projectcat.jarvis.memory.ChatEntry

data class ChatCompletionMessage(val role: String, val content: String)

/** Pure request policy for provider role mapping and optional system instructions. */
object ChatCompletionPayloadPolicy {
    const val MAX_HISTORY_MESSAGES = 24

    fun messages(history: List<ChatEntry>, systemInstruction: String? = null): List<ChatCompletionMessage> =
        buildList {
            if (!systemInstruction.isNullOrBlank()) {
                add(ChatCompletionMessage("system", systemInstruction))
            }
            history.takeLast(MAX_HISTORY_MESSAGES).forEach { entry ->
                val role = if (entry.role.equals("Jarvis", ignoreCase = true)) "assistant" else "user"
                add(ChatCompletionMessage(role, entry.text))
            }
        }
}
