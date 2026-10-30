package com.projectcat.jarvis.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OfflineModelState(
    val ready: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

/**
 * Owns one LiteRT-LM engine and conversation on a single background thread.
 * The model file is kept outside the APK in app-specific storage.
 */
class OfflineModelRunner(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null

    private val mutableState = MutableStateFlow(OfflineModelState())
    val state: StateFlow<OfflineModelState> = mutableState.asStateFlow()

    fun load(modelFile: File) {
        if (closed.get()) return
        scope.launch {
            mutex.withLock {
                if (loadedPath == modelFile.absolutePath && engine != null && conversation != null) {
                    mutableState.value = OfflineModelState(ready = true)
                    return@withLock
                }
                mutableState.value = OfflineModelState(loading = true)
                releaseLocked()
                try {
                    require(modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES) {
                        "The offline model file is missing or incomplete. Download and verify it first."
                    }
                    val candidate = Engine(
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU(),
                            cacheDir = appContext.cacheDir.path
                        )
                    )
                    // Do not close candidate if initialize throws; LiteRT-LM requires this lifecycle rule.
                    candidate.initialize()
                    engine = candidate
                    conversation = candidate.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "You are Jarvis, a concise personal assistant. " +
                                    "You are running fully offline. Do not claim to access device features " +
                                    "unless an application tool actually performed the action."
                            )
                        )
                    )
                    loadedPath = modelFile.absolutePath
                    mutableState.value = OfflineModelState(ready = true)
                } catch (error: Exception) {
                    releaseLocked()
                    mutableState.value = OfflineModelState(
                        error = error.message ?: "Could not load the offline model."
                    )
                }
            }
        }
    }

    suspend fun generate(prompt: String): String = withContext(dispatcher) {
        mutex.withLock {
            val activeConversation = conversation
                ?: throw IllegalStateException("Load the offline model before selecting offline chat.")
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            try {
                val output = StringBuilder()
                activeConversation.sendMessageAsync(prompt).collect { chunk ->
                    output.append(chunk.toString())
                }
                output.toString().ifBlank {
                    throw IllegalStateException("The offline model returned an empty response.")
                }
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = error.message ?: "Offline inference failed."
                )
                throw error
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }

    fun unload() {
        if (closed.get()) return
        scope.launch {
            mutex.withLock {
                releaseLocked()
                mutableState.value = OfflineModelState()
            }
        }
    }

    private fun releaseLocked() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        loadedPath = null
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.launch {
            mutex.withLock {
                releaseLocked()
                mutableState.value = OfflineModelState()
            }
            executor.shutdown()
        }.invokeOnCompletion {
            scope.cancel()
            dispatcher.close()
        }
    }

    private companion object {
        const val MIN_MODEL_BYTES = 600_000_000L
    }
}
