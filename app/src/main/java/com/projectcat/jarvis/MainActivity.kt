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
import androidx.compose.ui.unit.dp
import com.projectcat.jarvis.memory.ChatHistoryStore
import com.projectcat.jarvis.memory.ChatEntry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val historyStore = ChatHistoryStore(applicationContext)
        setContent { MaterialTheme { JarvisHome(historyStore) } }
    }
}

@Composable
private fun JarvisHome(historyStore: ChatHistoryStore) {
    var prompt by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(historyStore.load().ifEmpty {
            listOf(ChatEntry("Jarvis", "Jarvis foundation is running. AI providers and device tools are not connected yet."))
        })
    }

    fun appendMessage(role: String, text: String) {
        val updated = messages + ChatEntry(role, text)
        messages = updated
        historyStore.save(updated)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("JARVIS", style = MaterialTheme.typography.headlineLarge)
        Text("Personal AI assistant • Local-first foundation")
        Spacer(Modifier.height(12.dp))
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
            label = { Text("Give Jarvis a command") }
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = {
                val command = prompt.trim()
                if (command.isNotEmpty()) {
                    appendMessage("You", command)
                    appendMessage("Jarvis", localReply(command))
                    prompt = ""
                }
            }, modifier = Modifier.weight(1f)) { Text("Send") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                appendMessage("Jarvis", "Voice input is not connected yet. Use Send for now.")
            }) { Text("Voice (planned)") }
        }
    }
}

private fun localReply(command: String): String = when (command.lowercase()) {
    "hi", "hello", "hey jarvis" -> "Hello. I am ready. No external action was taken."
    "help" -> "This foundation currently supports a basic chat interface and locally saved conversation history. AI, voice, and device tools are not implemented yet."
    else -> "I received: \"$command\". I have not performed an action because no approved tool is connected for this request."
}
