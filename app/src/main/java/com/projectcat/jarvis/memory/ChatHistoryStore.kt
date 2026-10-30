package com.projectcat.jarvis.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatEntry(val role: String, val text: String)

class ChatHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_local_memory", Context.MODE_PRIVATE)

    fun load(): List<ChatEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val role = item.optString("role")
                    val text = item.optString("text")
                    if (role.isNotBlank() && text.isNotBlank()) add(ChatEntry(role, text))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(entries: List<ChatEntry>) {
        val json = JSONArray()
        entries.takeLast(MAX_MESSAGES).forEach { entry ->
            json.put(JSONObject().put("role", entry.role).put("text", entry.text))
        }
        // apply() writes asynchronously; chat remains available across ordinary app restarts.
        prefs.edit().putString(KEY_HISTORY, json.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val KEY_HISTORY = "chat_history_v1"
        const val MAX_MESSAGES = 300
    }
}
