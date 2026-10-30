package com.projectcat.jarvis.ai

import com.projectcat.jarvis.memory.ChatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface ChatCompletionProvider {
    suspend fun reply(history: List<ChatEntry>): String
}

data class ProviderSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini"
)

/** Normalizes both a provider base URL and a full chat-completions URL. */
object ChatCompletionsEndpoint {
    fun from(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        require(clean.startsWith("https://")) {
            "Provider URL must use HTTPS to protect API credentials in transit."
        }
        return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
    }
}

class OpenAiCompatibleProvider(
    private val settings: ProviderSettings,
    private val apiKey: String
) : ChatCompletionProvider {
    override suspend fun reply(history: List<ChatEntry>): String = withContext(Dispatchers.IO) {
        val endpoint = ChatCompletionsEndpoint.from(settings.baseUrl)
        val payload = JSONObject()
            .put("model", settings.model.trim())
            .put("messages", JSONArray().apply {
                history.takeLast(24).forEach { entry ->
                    val role = if (entry.role.equals("Jarvis", true)) "assistant" else "user"
                    put(JSONObject().put("role", role).put("content", entry.text))
                }
            })
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("AI provider returned HTTP $status. Check the provider URL, model, and credentials.")
            }
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val response = JSONObject(responseText)
            val content = response.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
            if (content.isNullOrBlank()) {
                throw IllegalStateException("The AI provider returned no message content.")
            }
            content
        } finally {
            connection.disconnect()
        }
    }
}
