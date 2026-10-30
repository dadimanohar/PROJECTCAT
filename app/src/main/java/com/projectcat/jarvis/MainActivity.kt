package com.projectcat.jarvis

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
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
import com.projectcat.jarvis.ai.OfflineModelRunner
import com.projectcat.jarvis.ai.OfflineModelStore
import com.projectcat.jarvis.memory.ChatHistoryStore
import com.projectcat.jarvis.memory.ChatEntry
import com.projectcat.jarvis.memory.MemoryCommand
import com.projectcat.jarvis.memory.MemoryCommandPolicy
import com.projectcat.jarvis.security.ProviderCredentialStore
import com.projectcat.jarvis.tools.LocalCommand
import com.projectcat.jarvis.tools.LocalCommandPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val modelStore = remember(context) { OfflineModelStore(context) }
    val offlineRunner = remember(context) { OfflineModelRunner(context) }
    val offlineState by offlineRunner.state.collectAsState()

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
    var useOfflineModel by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }

    var modelVerified by remember {
        mutableStateOf(runCatching { modelStore.isVerifiedModelPresent() }.getOrDefault(false))
    }
    var downloadId by remember { mutableStateOf(modelStore.savedDownloadId()) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadActive by remember { mutableStateOf(downloadId != null && !modelVerified) }
    var downloadStatus by remember {
        mutableStateOf(if (modelVerified) "Model downloaded and verified." else "Offline model not installed.")
    }
    var availableRam by remember { mutableLongStateOf(modelStore.availableMemoryBytes()) }

    val scope = rememberCoroutineScope()

    DisposableEffect(offlineRunner) {
        onDispose { offlineRunner.close() }
    }

    LaunchedEffect(Unit) {
        if (!modelVerified) {
            val existingFile = runCatching { modelStore.modelFile() }.getOrNull()
            if (existingFile?.isFile == true) {
                val verified = withContext(Dispatchers.IO) { modelStore.verifyDownloadedModel() }
                modelVerified = verified
                downloadStatus = if (verified) {
                    "Existing model file passed SHA-256 verification."
                } else {
                    "Existing model file failed verification and was removed."
                }
            }
        }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val progress = withContext(Dispatchers.IO) { modelStore.queryDownload(id) }
            if (progress == null) {
                if (!modelVerified) downloadStatus = "Download record not found. You can retry."
                downloadActive = false
                break
            }
            downloadProgress = progress.percent
            when (progress.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadActive = false
                    val verified = withContext(Dispatchers.IO) { modelStore.verifyDownloadedModel() }
                    modelVerified = verified
                    downloadStatus = if (verified) {
                        "Download complete. SHA-256 verified. Tap Load model."
                    } else {
                        "Model integrity check failed. The downloaded file was deleted; retry the download."
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    downloadActive = false
                    downloadStatus = "Download failed (reason ${progress.reason}). You can retry."
                    break
                }
                DownloadManager.STATUS_PAUSED -> {
                    downloadActive = true
                    downloadStatus = "Download paused (reason ${progress.reason}). Connect to Wi-Fi."
                    delay(1500)
                }
                else -> {
                    downloadActive = true
                    downloadStatus = "Downloading offline model: ${progress.percent}%"
                    delay(1000)
                }
            }
        }
    }

    LaunchedEffect(offlineState.ready, offlineState.error) {
        if (offlineState.ready) downloadStatus = "Offline model loaded and ready (CPU backend)."
        offlineState.error?.let { downloadStatus = "Model load failed: $it" }
    }

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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("JARVIS", style = MaterialTheme.typography.headlineLarge)
                Text("Personal AI assistant • Online and offline modes")
            }
            TextButton(onClick = { showClearHistoryDialog = true }) { Text("Clear history") }
        }
        Spacer(Modifier.height(8.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Offline model", style = MaterialTheme.typography.titleMedium)
                Text("${OfflineModelStore.MODEL_NAME} • ${OfflineModelStore.MODEL_SIZE_LABEL} • CPU inference")
                Text("Wi-Fi-only download. Model stays outside the APK.")
                Text(
                    "Free RAM now: ${String.format(Locale.US, "%.1f", availableRam / 1_000_000_000.0)} GB. " +
                        "This model may need substantial additional RAM; 4 GB devices can run out of memory."
                )
                Text(downloadStatus, style = MaterialTheme.typography.bodySmall)
                Text("Safety gate: loading is disabled unless at least 2.8 GB RAM is currently available.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { availableRam = modelStore.availableMemoryBytes() }) { Text("Refresh RAM status") }
                if (downloadActive) {
                    Text("Download progress: $downloadProgress%")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !downloadActive && !modelVerified,
                        onClick = {
                            try {
                                downloadId = modelStore.startDownload()
                                downloadActive = true
                                downloadProgress = 0
                                downloadStatus = "Download queued. Wi-Fi connection required."
                            } catch (error: Exception) {
                                downloadStatus = error.message ?: "Could not start model download."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (modelVerified) "Downloaded" else "Download model") }
                    Button(
                        enabled = modelVerified && !offlineState.loading && !offlineState.ready &&
                            com.projectcat.jarvis.ai.OfflineModelReadinessPolicy.canLoad(availableRam),
                        onClick = {
                            availableRam = modelStore.availableMemoryBytes()
                            if (!com.projectcat.jarvis.ai.OfflineModelReadinessPolicy.canLoad(availableRam)) {
                                downloadStatus = "Not loaded: available RAM is below the 2.8 GB safety threshold."
                            } else {
                                offlineRunner.load(modelStore.modelFile())
                                downloadStatus = "Loading model on CPU in the background…"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (offlineState.loading) "Loading…" else "Load model") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Use offline AI")
                    Switch(
                        checked = useOfflineModel,
                        onCheckedChange = { useOfflineModel = it },
                        enabled = offlineState.ready && !offlineState.busy
                    )
                }
                if (offlineState.ready) {
                    Text("Offline model is loaded. Chat stays on-device when offline mode is selected.")
                    TextButton(onClick = {
                        useOfflineModel = false
                        offlineRunner.unload()
                        downloadStatus = "Offline model unloaded to release memory."
                    }) { Text("Unload model and free RAM") }
                }
                offlineState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (!modelVerified) {
                    TextButton(
                        enabled = !downloadActive,
                        onClick = {
                            modelStore.deleteModel()
                            downloadId = null
                            downloadProgress = 0
                            downloadStatus = "Offline model download state cleared."
                        }
                    ) { Text("Clear failed download") }
                } else {
                    TextButton(
                        enabled = !offlineState.ready && !offlineState.loading && !offlineState.busy,
                        onClick = {
                        useOfflineModel = false
                        modelStore.deleteModel()
                        modelVerified = false
                        downloadId = null
                        downloadStatus = "Offline model deleted from this device."
                    }) { Text("Delete downloaded model") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Online AI provider settings", style = MaterialTheme.typography.titleMedium)
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
            label = { Text(if (useOfflineModel) "Ask Jarvis offline" else "Ask Jarvis") }
        )
        Text("Commands: /help • /open • /search • /maps • /dial • /remember • /recall • /memories • /forget • /forget-all", style = MaterialTheme.typography.bodySmall)
        Text(voiceStatus, style = MaterialTheme.typography.bodySmall)
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear conversation history?") },
                text = { Text("This permanently deletes Jarvis's locally stored chat history from this device. It does not delete provider settings or saved memory facts.") },
                confirmButton = {
                    TextButton(onClick = {
                        historyStore.clear()
                        messages = emptyList()
                        showClearHistoryDialog = false
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
                }
            )
        }
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
                enabled = !isSending && prompt.isNotBlank() && (!useOfflineModel || offlineState.ready),
                onClick = {
                    val command = prompt.trim()
                    prompt = ""
                    appendMessage("You", command)
                    val localCommandReply = when (val localCommand = LocalCommandPolicy.parse(command)) {
                        null -> null
                        LocalCommand.Help -> "Jarvis local commands:\n" +
                            "/open https://example.com — open a user-supplied HTTPS link in your browser.\n" +
                            "/search <query> — open a web search in your browser.\n" +
                            "/maps <place> — open a map search.\n" +
                            "/dial <number> — open the dialer with a number prefilled; it will not place a call.\n" +
                            "/remember <fact>, /recall <keyword>, /memories, /forget <exact fact>, /forget-all — manage local memory."
                        is LocalCommand.Invalid -> localCommand.message
                        is LocalCommand.OpenUrl -> {
                            try {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(localCommand.url)),
                                        "Open link with"
                                    )
                                )
                                "Opening the requested link in an app you choose."
                            } catch (error: Exception) {
                                "Could not open the link: ${error.message ?: "No compatible browser is available."}"
                            }
                        }
                        is LocalCommand.MapsSearch -> {
                            try {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(localCommand.url)),
                                        "Find this place with"
                                    )
                                )
                                "Opening the requested place in a map app you choose."
                            } catch (error: Exception) {
                                "Could not open maps: ${error.message ?: "No compatible map app is available."}"
                            }
                        }
                        is LocalCommand.DialNumber -> {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(localCommand.uri)))
                                "Opening the dialer with the number filled in. Review it and tap Call yourself."
                            } catch (error: Exception) {
                                "Could not open the dialer: ${error.message ?: "No compatible dialer is available."}"
                            }
                        }
                        is LocalCommand.SearchWeb -> {
                            try {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(localCommand.url)),
                                        "Search the web with"
                                    )
                                )
                                "Opening your web search in an app you choose."
                            } catch (error: Exception) {
                                "Could not open web search: ${error.message ?: "No compatible browser is available."}"
                            }
                        }
                    }
                    val localMemoryReply = localCommandReply ?: when (
                        val memoryCommand = MemoryCommandPolicy.parse(command)
                    ) {
                        null -> null
                        is MemoryCommand.Invalid -> memoryCommand.message
                        is MemoryCommand.Remember -> {
                            if (historyStore.rememberFact(memoryCommand.fact)) {
                                "Saved that fact in local memory on this device."
                            } else {
                                "Memory was not saved. Provide a fact between 1 and 500 characters."
                            }
                        }
                        is MemoryCommand.Recall -> {
                            val matches = historyStore.searchFacts(memoryCommand.query)
                            if (matches.isEmpty()) {
                                "No saved memory matched that keyword."
                            } else {
                                "Saved memories:\n" + matches.joinToString("\n") { "• $it" }
                            }
                        }
                        is MemoryCommand.Forget -> {
                            if (historyStore.forgetFact(memoryCommand.fact)) {
                                "Deleted that exact fact from local memory."
                            } else {
                                "No exact matching saved fact was found."
                            }
                        }
                        MemoryCommand.ListFacts -> {
                            val facts = historyStore.listFacts()
                            if (facts.isEmpty()) {
                                "There are no saved memory facts on this device."
                            } else {
                                "Saved memory facts (latest ${facts.size}):\n" +
                                    facts.mapIndexed { index, fact -> "${index + 1}. $fact" }.joinToString("\n")
                            }
                        }
                        MemoryCommand.ConfirmClearAll -> {
                            val deleted = historyStore.clearFacts()
                            "Deleted $deleted saved memory fact(s) from this device."
                        }
                        MemoryCommand.RequestClearAllConfirmation ->
                            "This permanently deletes all saved memory facts. To confirm, send /forget-all confirm."
                    }
                    if (localMemoryReply != null) {
                        appendMessage("Jarvis", localMemoryReply)
                    } else {
                        val requestHistory = messages.takeLast(24)
                        isSending = true
                        scope.launch {
                            try {
                                val response = if (useOfflineModel) {
                                    offlineRunner.generate(command)
                                } else {
                                    credentialStore.createProvider().reply(requestHistory)
                                }
                                appendMessage("Jarvis", response)
                                if (speakReplies && ttsReady) {
                                    textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
                                }
                            } catch (error: Exception) {
                                appendMessage(
                                    "Jarvis",
                                    if (useOfflineModel) {
                                        "Offline AI failed: ${error.message ?: "Unknown inference error"}. Online fallback was not used."
                                    } else {
                                        "AI request failed: ${error.message ?: "Unknown provider error"}. Check your connection and provider settings."
                                    }
                                )
                            } finally {
                                isSending = false
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (isSending) "Thinking…" else "Send") }
        }
    }
}
