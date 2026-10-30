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

private data class Message(val role: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { JarvisHome() } }
    }
}

@Composable
private fun JarvisHome() {
    var prompt by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(listOf(Message("Jarvis", "Jarvis foundation is running. AI providers and device tools are not connected yet.")))
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
        Button(onClick = {
            val command = prompt.trim()
            if (command.isNotEmpty()) {
                messages = messages + Message("You", command) +
                    Message("Jarvis", localReply(command))
                prompt = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Send") }
    }
}

private fun localReply(command: String): String = when (command.lowercase()) {
    "hi", "hello", "hey jarvis" -> "Hello. I am ready. No external action was taken."
    "help" -> "This foundation currently supports a basic chat interface only. AI, memory, voice, and device tools are not implemented yet."
    else -> "I received: \"$command\". I have not performed an action because no approved tool is connected for this request."
}
