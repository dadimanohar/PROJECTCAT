package com.projectcat.jarvis.ai

import com.projectcat.jarvis.memory.ChatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

interface ChatCompletionProvider {
    suspend fun reply(history: List<ChatEntry>): String

    suspend fun replyWithSystemInstruction(
        systemInstruction: String,
        history: List<ChatEntry>
    ): String
}

data class ProviderSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini"
)

/** Normalizes both a provider base URL and a full chat-completions URL. */
object ChatCompletionsEndpoint {
    fun from(baseUrl: String): String {
        val input = baseUrl.trim()
        val uri = try { URI(input) } catch (_: Exception) {
            throw IllegalArgumentException("Provider URL is not a valid URL.")
        }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Provider URL must be a valid HTTPS URL."
        }
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Provider URL must not contain credentials, query parameters, or fragments."
        }
        val clean = input.trimEnd('/')
        return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
    }
}

class OpenAiCompatibleProvider(
    private val settings: ProviderSettings,
    private val apiKey: String
) : ChatCompletionProvider {
    override suspend fun reply(history: List<ChatEntry>): String =
        complete(history, systemInstruction = null)

    override suspend fun replyWithSystemInstruction(
        systemInstruction: String,
        history: List<ChatEntry>
    ): String {
        require(systemInstruction.isNotBlank()) { "System instruction cannot be empty." }
        return complete(history, systemInstruction)
    }

    private suspend fun complete(
        history: List<ChatEntry>,
        systemInstruction: String?
    ): String = withContext(Dispatchers.IO) {
        val endpoint = ChatCompletionsEndpoint.from(settings.baseUrl)
        val payload = JSONObject()
            .put("model", settings.model.trim())
            .put("messages", JSONArray().apply {
                ChatCompletionPayloadPolicy.messages(history, systemInstruction).forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = false
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
