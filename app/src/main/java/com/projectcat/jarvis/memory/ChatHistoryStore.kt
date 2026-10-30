package com.projectcat.jarvis.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

data class ChatEntry(val role: String, val text: String)
data class ActionAuditEntry(val actionKey: String, val outcome: String, val createdAt: Long)
data class LocalNote(val id: Long, val text: String, val createdAt: Long)

object ActionAuditPolicy {
    const val HANDED_OFF = "handed_off"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    private val allowedActions = setOf("open_url", "web_search", "maps_search", "dial_number")
    private val allowedOutcomes = setOf(HANDED_OFF, FAILED, CANCELLED)

    fun isValid(actionKey: String, outcome: String): Boolean =
        actionKey in allowedActions && outcome in allowedOutcomes
}

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
    fun listFacts(limit: Int = 50): List<String> {
        val results = mutableListOf<String>()
        helper.readableDatabase.query(
            TABLE_FACTS,
            arrayOf(COLUMN_FACT),
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC, $COLUMN_FACT_ID DESC",
            limit.coerceIn(1, 50).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) results += cursor.getString(0)
        }
        return results
    }

    @Synchronized
    fun clearFacts(): Int =
        helper.writableDatabase.delete(TABLE_FACTS, null, null)

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

    @Synchronized
    fun addNote(text: String): Long {
        val clean = LocalNotePolicy.validate(text) ?: return -1L
        val values = android.content.ContentValues().apply {
            put(COLUMN_NOTE_TEXT, clean)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        val db = helper.writableDatabase
        val id = db.insert(TABLE_NOTES, null, values)
        if (id == -1L) return -1L
        db.execSQL(
            "DELETE FROM $TABLE_NOTES WHERE $COLUMN_NOTE_ID NOT IN " +
                "(SELECT $COLUMN_NOTE_ID FROM $TABLE_NOTES ORDER BY $COLUMN_NOTE_ID DESC LIMIT ${LocalNotePolicy.MAX_NOTES})"
        )
        return id
    }

    @Synchronized
    fun listNotes(limit: Int = 30): List<LocalNote> {
        val results = mutableListOf<LocalNote>()
        helper.readableDatabase.query(
            TABLE_NOTES,
            arrayOf(COLUMN_NOTE_ID, COLUMN_NOTE_TEXT, COLUMN_CREATED_AT),
            null,
            null,
            null,
            null,
            "$COLUMN_NOTE_ID DESC",
            limit.coerceIn(1, 50).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += LocalNote(
                    id = cursor.getLong(0),
                    text = cursor.getString(1),
                    createdAt = cursor.getLong(2)
                )
            }
        }
        return results
    }

    @Synchronized
    fun deleteNote(noteId: Long): Boolean {
        if (noteId <= 0L) return false
        return helper.writableDatabase.delete(
            TABLE_NOTES,
            "$COLUMN_NOTE_ID = ?",
            arrayOf(noteId.toString())
        ) > 0
    }

    @Synchronized
    fun recordActionAudit(actionKey: String, outcome: String): Boolean {
        if (!ActionAuditPolicy.isValid(actionKey, outcome)) return false
        val values = android.content.ContentValues().apply {
            put(COLUMN_ACTION_KEY, actionKey)
            put(COLUMN_ACTION_OUTCOME, outcome)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        val db = helper.writableDatabase
        if (db.insert(TABLE_ACTION_AUDIT, null, values) == -1L) return false
        db.execSQL(
            "DELETE FROM $TABLE_ACTION_AUDIT WHERE $COLUMN_ACTION_ID NOT IN " +
                "(SELECT $COLUMN_ACTION_ID FROM $TABLE_ACTION_AUDIT ORDER BY $COLUMN_ACTION_ID DESC LIMIT 100)"
        )
        return true
    }

    @Synchronized
    fun loadActionAudit(limit: Int = 30): List<ActionAuditEntry> {
        val results = mutableListOf<ActionAuditEntry>()
        helper.readableDatabase.query(
            TABLE_ACTION_AUDIT,
            arrayOf(COLUMN_ACTION_KEY, COLUMN_ACTION_OUTCOME, COLUMN_CREATED_AT),
            null,
            null,
            null,
            null,
            "$COLUMN_ACTION_ID DESC",
            limit.coerceIn(1, 30).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += ActionAuditEntry(
                    actionKey = cursor.getString(0),
                    outcome = cursor.getString(1),
                    createdAt = cursor.getLong(2)
                )
            }
        }
        return results
    }

    @Synchronized
    fun clearActionAudit(): Int =
        helper.writableDatabase.delete(TABLE_ACTION_AUDIT, null, null)

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
            createActionAuditTable(db)
            createNotesTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createFactsTable(db)
            if (oldVersion < 3) createActionAuditTable(db)
            if (oldVersion < 4) createNotesTable(db)
        }

        private fun createActionAuditTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_ACTION_AUDIT (
                    $COLUMN_ACTION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_ACTION_KEY TEXT NOT NULL,
                    $COLUMN_ACTION_OUTCOME TEXT NOT NULL,
                    $COLUMN_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        private fun createNotesTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                    $COLUMN_NOTE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_NOTE_TEXT TEXT NOT NULL,
                    $COLUMN_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
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
        const val DATABASE_VERSION = 4
        const val TABLE_MESSAGES = "chat_messages"
        const val TABLE_FACTS = "memory_facts"
        const val TABLE_ACTION_AUDIT = "action_audit"
        const val TABLE_NOTES = "local_notes"
        const val COLUMN_NOTE_ID = "note_id"
        const val COLUMN_NOTE_TEXT = "note_text"
        const val COLUMN_ACTION_ID = "event_id"
        const val COLUMN_ACTION_KEY = "action_key"
        const val COLUMN_ACTION_OUTCOME = "outcome"
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
