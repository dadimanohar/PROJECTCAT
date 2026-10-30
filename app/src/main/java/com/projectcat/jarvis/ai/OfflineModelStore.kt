package com.projectcat.jarvis.ai

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.security.MessageDigest

data class ModelDownloadProgress(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val percent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

class OfflineModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = appContext.getSharedPreferences("jarvis_offline_model", Context.MODE_PRIVATE)

    fun modelFile(): File {
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("App-specific download storage is unavailable.")
        return File(directory, MODEL_FILE_NAME)
    }

    fun savedDownloadId(): Long? =
        prefs.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID).takeIf { it != NO_DOWNLOAD_ID }

    fun availableMemoryBytes(): Long {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }

    fun isVerifiedModelPresent(): Boolean =
        prefs.getBoolean(KEY_VERIFIED, false) &&
            modelFile().let { it.exists() && it.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES }

    fun startDownload(): Long {
        val file = modelFile()
        val parent = file.parentFile ?: throw IllegalStateException("Model storage path is unavailable.")
        if (StatFs(parent.path).availableBytes < REQUIRED_FREE_BYTES) {
            throw IllegalStateException("At least 1 GB of free storage is required before downloading the 614 MB model.")
        }
        file.delete()
        prefs.edit().putBoolean(KEY_VERIFIED, false).apply()

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Jarvis offline model")
            .setDescription("Downloading Qwen3 0.6B for offline chat (614 MB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                MODEL_FILE_NAME
            )

        return downloadManager.enqueue(request).also { id ->
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        }
    }

    fun queryDownload(id: Long): ModelDownloadProgress? {
        val query = DownloadManager.Query().setFilterById(id)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ModelDownloadProgress(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                ),
                totalBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                ),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            )
        }
    }

    fun verifyDownloadedModel(): Boolean {
        val file = modelFile()
        if (!file.isFile || file.length() !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) return false

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val verified = actual.equals(EXPECTED_SHA256, ignoreCase = true)
        prefs.edit().putBoolean(KEY_VERIFIED, verified).apply()
        if (!verified) file.delete()
        return verified
    }

    fun deleteModel() {
        modelFile().delete()
        savedDownloadId()?.let { downloadManager.remove(it) }
        prefs.edit()
            .remove(KEY_DOWNLOAD_ID)
            .putBoolean(KEY_VERIFIED, false)
            .apply()
    }

    companion object {
        const val MODEL_NAME = "Qwen3 0.6B (LiteRT-LM)"
        const val MODEL_FILE_NAME = "Qwen3-0.6B.litertlm"
        const val MODEL_SIZE_LABEL = "614 MB"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
        const val EXPECTED_SHA256 =
            "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"

        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_VERIFIED = "model_verified"
        private const val NO_DOWNLOAD_ID = -1L
        private const val MIN_MODEL_BYTES = 600_000_000L
        private const val MAX_MODEL_BYTES = 630_000_000L
        private const val REQUIRED_FREE_BYTES = 1_000_000_000L
    }
}
