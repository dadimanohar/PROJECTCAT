package com.projectcat.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.projectcat.jarvis.memory.ChatHistoryStore
import com.projectcat.jarvis.memory.ChatEntry
import com.projectcat.jarvis.security.ProviderCredentialStore
import kotlinx.coroutines.launch
import java.util.Locale

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
    val context = LocalContext.current
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
    var voiceStatus by remember { mutableStateOf("Voice input is push-to-talk; it does not listen in the background.") }
    var speakReplies by remember { mutableStateOf(true) }
    var ttsReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val textToSpeech = remember(context) {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(textToSpeech) {
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
    LaunchedEffect(ttsReady) {
        if (ttsReady) textToSpeech.language = Locale.getDefault()
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    DisposableEffect(speechRecognizer) {
        if (speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { voiceStatus = "Listening… speak now." }
                override fun onBeginningOfSpeech() { voiceStatus = "Listening…" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { voiceStatus = "Processing speech…" }
                override fun onError(error: Int) {
                    voiceStatus = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognised. Try again."
                        SpeechRecognizer.ERROR_NETWORK -> "Speech service network error. Check the device speech service."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recogniser is busy. Try again shortly."
                        else -> "Speech recognition failed (code $error)."
                    }
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        prompt = spoken
                        voiceStatus = "Speech added to the message. Review it, then press Send."
                    } else {
                        voiceStatus = "No speech recognised. Try again."
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            voiceStatus = "Speech recognition is not available on this device."
        }
        onDispose { speechRecognizer?.destroy() }
    }

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            voiceStatus = "Speech recognition is not available on this device."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizer.startListening(intent)
        } catch (error: Exception) {
            voiceStatus = "Could not start speech recognition: ${error.message ?: "unknown error"}"
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else voiceStatus = "Microphone permission denied. Voice input remains disabled."
    }

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
                        settingsMessage = "Saved API key cleared."
                    }) { Text("Clear key") }
                }
                Text(settingsMessage, style = MaterialTheme.typography.bodySmall)
                Text("Only HTTPS endpoints are accepted. Never paste API keys into chat.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Speak Jarvis replies")
            Switch(checked = speakReplies, onCheckedChange = { speakReplies = it })
        }
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
        Text(voiceStatus, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !isSending,
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        startListening()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("🎙 Speak") }
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
                            if (speakReplies && ttsReady) {
                                textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
                            }
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
                modifier = Modifier.weight(1f)
            ) { Text(if (isSending) "Thinking…" else "Send") }
        }
    }
}
