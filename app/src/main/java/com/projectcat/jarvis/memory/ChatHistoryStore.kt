package com.projectcat.jarvis.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

data class ChatEntry(val role: String, val text: String)

object ChatHistoryPolicy {
    const val MAX_MESSAGES = 300

    fun retain(entries: List<ChatEntry>): List<ChatEntry> =
        entries.filter { it.role.isNotBlank() && it.text.isNotBlank() }.takeLast(MAX_MESSAGES)
}

/**
 * SQLite-backed local conversation history.
 *
 * The first read migrates the previous SharedPreferences JSON history once.
 * Only the newest 300 valid messages are retained.
 */
class ChatHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val helper = HistoryDatabase(appContext)
    private val legacyPrefs = appContext.getSharedPreferences("jarvis_local_memory", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<ChatEntry> {
        migrateLegacyHistoryIfNeeded()
        val result = mutableListOf<ChatEntry>()
        helper.readableDatabase.query(
            TABLE_MESSAGES,
            arrayOf(COLUMN_ROLE, COLUMN_TEXT),
            null,
            null,
            null,
            null,
            "$COLUMN_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += ChatEntry(
                    role = cursor.getString(0),
                    text = cursor.getString(1)
                )
            }
        }
        return ChatHistoryPolicy.retain(result)
    }

    @Synchronized
    fun save(entries: List<ChatEntry>) {
        val retained = ChatHistoryPolicy.retain(entries)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_MESSAGES, null, null)
            retained.forEach { entry ->
                db.execSQL(
                    "INSERT INTO $TABLE_MESSAGES ($COLUMN_ROLE, $COLUMN_TEXT, $COLUMN_CREATED_AT) VALUES (?, ?, ?)",
                    arrayOf(entry.role, entry.text, System.currentTimeMillis())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        legacyPrefs.edit().remove(LEGACY_HISTORY_KEY).putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
    }

    @Synchronized
    fun clear() {
        helper.writableDatabase.delete(TABLE_MESSAGES, null, null)
        legacyPrefs.edit()
            .remove(LEGACY_HISTORY_KEY)
            .putBoolean(MIGRATION_COMPLETE_KEY, true)
            .apply()
    }

    private fun migrateLegacyHistoryIfNeeded() {
        if (legacyPrefs.getBoolean(MIGRATION_COMPLETE_KEY, false)) return

        val raw = legacyPrefs.getString(LEGACY_HISTORY_KEY, null)
        val legacyEntries = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val role = item.optString("role")
                        val text = item.optString("text")
                        if (role.isNotBlank() && text.isNotBlank()) add(ChatEntry(role, text))
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            ChatHistoryPolicy.retain(legacyEntries).forEach { entry ->
                db.execSQL(
                    "INSERT INTO $TABLE_MESSAGES ($COLUMN_ROLE, $COLUMN_TEXT, $COLUMN_CREATED_AT) VALUES (?, ?, ?)",
                    arrayOf(entry.role, entry.text, System.currentTimeMillis())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        legacyPrefs.edit().remove(LEGACY_HISTORY_KEY).putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
    }

    private class HistoryDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_MESSAGES (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_ROLE TEXT NOT NULL,
                    $COLUMN_TEXT TEXT NOT NULL,
                    $COLUMN_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_chat_created_at ON $TABLE_MESSAGES ($COLUMN_ID)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No released database schema exists yet. Future schema changes must use explicit migrations.
        }
    }

    private companion object {
        const val DATABASE_NAME = "jarvis_memory.db"
        const val DATABASE_VERSION = 1
        const val TABLE_MESSAGES = "chat_messages"
        const val COLUMN_ID = "id"
        const val COLUMN_ROLE = "role"
        const val COLUMN_TEXT = "message_text"
        const val COLUMN_CREATED_AT = "created_at"
        const val LEGACY_HISTORY_KEY = "chat_history_v1"
        const val MIGRATION_COMPLETE_KEY = "chat_history_sqlite_migrated_v1"
    }
}
