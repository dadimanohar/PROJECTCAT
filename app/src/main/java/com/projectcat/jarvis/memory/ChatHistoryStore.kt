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

object MemoryFactPolicy {
    const val MAX_FACT_LENGTH = 500

    fun normalize(fact: String): String = fact.trim().replace(Regex("\\s+"), " ").lowercase()

    fun validate(fact: String): String? {
        val clean = fact.trim().replace(Regex("\\s+"), " ")
        return clean.takeIf { it.isNotBlank() && it.length <= MAX_FACT_LENGTH }
    }
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
                    arrayOf<Any>(entry.role, entry.text, System.currentTimeMillis())
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

    @Synchronized
    fun rememberFact(fact: String): Boolean {
        val clean = MemoryFactPolicy.validate(fact) ?: return false
        val values = android.content.ContentValues().apply {
            put(COLUMN_FACT, clean)
            put(COLUMN_NORMALIZED_FACT, MemoryFactPolicy.normalize(clean))
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            TABLE_FACTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return true
    }

    @Synchronized
    fun searchFacts(query: String, limit: Int = 20): List<String> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()
        val results = mutableListOf<String>()
        helper.readableDatabase.query(
            TABLE_FACTS,
            arrayOf(COLUMN_FACT),
            "instr(lower($COLUMN_FACT), lower(?)) > 0",
            arrayOf(cleanQuery),
            null,
            null,
            "$COLUMN_CREATED_AT DESC",
            limit.coerceIn(1, 50).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) results += cursor.getString(0)
        }
        return results
    }

    @Synchronized
    fun forgetFact(fact: String): Boolean {
        val normalized = MemoryFactPolicy.normalize(fact)
        if (normalized.isBlank()) return false
        return helper.writableDatabase.delete(
            TABLE_FACTS,
            "$COLUMN_NORMALIZED_FACT = ?",
            arrayOf(normalized)
        ) > 0
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
                    arrayOf<Any>(entry.role, entry.text, System.currentTimeMillis())
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
            createFactsTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createFactsTable(db)
        }

        private fun createFactsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_FACTS (
                    $COLUMN_FACT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FACT TEXT NOT NULL,
                    $COLUMN_NORMALIZED_FACT TEXT NOT NULL UNIQUE,
                    $COLUMN_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private companion object {
        const val DATABASE_NAME = "jarvis_memory.db"
        const val DATABASE_VERSION = 2
        const val TABLE_MESSAGES = "chat_messages"
        const val TABLE_FACTS = "memory_facts"
        const val COLUMN_FACT_ID = "fact_id"
        const val COLUMN_FACT = "fact_text"
        const val COLUMN_NORMALIZED_FACT = "normalized_fact"
        const val COLUMN_ID = "id"
        const val COLUMN_ROLE = "role"
        const val COLUMN_TEXT = "message_text"
        const val COLUMN_CREATED_AT = "created_at"
        const val LEGACY_HISTORY_KEY = "chat_history_v1"
        const val MIGRATION_COMPLETE_KEY = "chat_history_sqlite_migrated_v1"
    }
}
