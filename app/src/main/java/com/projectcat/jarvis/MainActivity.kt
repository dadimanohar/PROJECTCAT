package com.projectcat.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.projectcat.jarvis.memory.ChatHistoryStore
import com.projectcat.jarvis.memory.ChatEntry
import com.projectcat.jarvis.security.ProviderCredentialStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val historyStore = ChatHistoryStore(applicationContext)
        val credentialStore = ProviderCredentialStore(applicationContext)
        setContent { MaterialTheme { JarvisHome(historyStore, credentialStore) } }
    }
}

@Composable
private fun JarvisHome(
    historyStore: ChatHistoryStore,
    credentialStore: ProviderCredentialStore
) {
    var prompt by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(historyStore.load().ifEmpty {
            listOf(ChatEntry("Jarvis", "Jarvis is ready. Configure an AI provider to enable online responses."))
        })
    }
    val savedSettings = remember { credentialStore.publicSettings() }
    var baseUrl by remember { mutableStateOf(savedSettings.baseUrl) }
    var model by remember { mutableStateOf(savedSettings.model) }
    var apiKey by remember { mutableStateOf("") }
    var settingsMessage by remember { mutableStateOf("API key is stored encrypted on this device.") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun appendMessage(role: String, text: String) {
        val updated = messages + ChatEntry(role, text)
        messages = updated
        historyStore.save(updated)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("JARVIS", style = MaterialTheme.typography.headlineLarge)
        Text("Personal AI assistant • Online provider support")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("AI provider settings", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("HTTPS API base URL") }
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model name") }
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("API key (leave blank to keep saved key)") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        try {
                            credentialStore.save(baseUrl, model, apiKey)
                            apiKey = ""
                            settingsMessage = "Provider settings saved. Credential encrypted with Android Keystore."
                        } catch (error: Exception) {
                            settingsMessage = error.message ?: "Could not save provider settings."
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Save provider") }
                    OutlinedButton(onClick = {
                        credentialStore.clearCredentials()
                        apiKey = ""
                        settingsMessage = "Saved API key cleared. Provider may still work if it does not require a key."
                    }) { Text("Clear key") }
                }
                Text(settingsMessage, style = MaterialTheme.typography.bodySmall)
                Text("Only HTTPS endpoints are accepted. Never paste API keys into chat.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(messages) { message ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(message.role, style = MaterialTheme.typography.labelMedium)
                        Text(message.text)
                    }
                }
            }
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending,
            label = { Text("Ask Jarvis") }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !isSending && prompt.isNotBlank(),
            onClick = {
                val command = prompt.trim()
                prompt = ""
                appendMessage("You", command)
                val requestHistory = messages.takeLast(24)
                isSending = true
                scope.launch {
                    try {
                        val response = credentialStore.createProvider().reply(requestHistory)
                        appendMessage("Jarvis", response)
                    } catch (error: Exception) {
                        appendMessage(
                            "Jarvis",
                            "AI request failed: ${error.message ?: "Unknown provider error"}. Check your connection and provider settings."
                        )
                    } finally {
                        isSending = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isSending) "Thinking…" else "Send") }
    }
}
