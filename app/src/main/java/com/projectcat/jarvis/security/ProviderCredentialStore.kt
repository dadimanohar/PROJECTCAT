package com.projectcat.jarvis.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.projectcat.jarvis.ai.ChatCompletionsEndpoint
import com.projectcat.jarvis.ai.OpenAiCompatibleProvider
import com.projectcat.jarvis.ai.ProviderSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PublicProviderSettings(val baseUrl: String, val model: String)

class ProviderCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_provider_settings", Context.MODE_PRIVATE)

    fun publicSettings(): PublicProviderSettings = PublicProviderSettings(
        prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
        prefs.getString(KEY_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
    )

    @Synchronized
    fun save(baseUrl: String, model: String, apiKey: String) {
        ChatCompletionsEndpoint.from(baseUrl)
        require(model.isNotBlank()) { "Model name cannot be empty." }
        val editor = prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_MODEL, model.trim())
        if (apiKey.isNotBlank()) editor.putString(KEY_ENCRYPTED_KEY, encrypt(apiKey.trim()))
        editor.apply()
    }

    fun createProvider(): OpenAiCompatibleProvider {
        val settings = publicSettings()
        val encrypted = prefs.getString(KEY_ENCRYPTED_KEY, null)
        val apiKey = encrypted?.let(::decrypt).orEmpty()
        return OpenAiCompatibleProvider(ProviderSettings(settings.baseUrl, settings.model), apiKey)
    }

    @Synchronized
    fun clearCredentials() {
        prefs.edit().remove(KEY_ENCRYPTED_KEY).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2) { "Saved provider credential is invalid. Please enter it again." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jarvis_provider_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BASE_URL = "provider_base_url"
        const val KEY_MODEL = "provider_model"
        const val KEY_ENCRYPTED_KEY = "provider_api_key_encrypted"
    }
}
