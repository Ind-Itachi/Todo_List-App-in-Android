package com.example.aahnik.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Local SQLite store.
 *
 *  aahnik      – the list of practices you track
 *  completion  – one row per aahnik per day, with the exact time it was marked done
 */
class AahnikDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE aahnik (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                icon TEXT NOT NULL,
                position INTEGER NOT NULL,
                created_on TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE completion (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                aahnik_id INTEGER NOT NULL REFERENCES aahnik(id) ON DELETE CASCADE,
                day TEXT NOT NULL,
                completed_at INTEGER NOT NULL,
                minute_of_day INTEGER NOT NULL,
                UNIQUE(aahnik_id, day)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_completion_day ON completion(day)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema; add migrations here when the schema changes.
    }

    fun getAahniks(): List<Aahnik> {
        val result = mutableListOf<Aahnik>()
        readableDatabase.rawQuery(
            "SELECT id, name, icon, position, created_on FROM aahnik ORDER BY position, id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                result += Aahnik(
                    id = c.getLong(0),
                    name = c.getString(1),
                    icon = c.getString(2),
                    position = c.getInt(3),
                    createdOn = LocalDate.parse(c.getString(4)),
                )
            }
        }
        return result
    }

    fun addAahnik(name: String, icon: String, today: LocalDate): Long {
        val db = writableDatabase
        val nextPosition = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM aahnik", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val values = ContentValues().apply {
            put("name", name)
            put("icon", icon)
            put("position", nextPosition)
            put("created_on", today.toString())
        }
        return db.insertOrThrow("aahnik", null, values)
    }

    fun updateAahnik(id: Long, name: String, icon: String) {
        val values = ContentValues().apply {
            put("name", name)
            put("icon", icon)
        }
        writableDatabase.update("aahnik", values, "id = ?", arrayOf(id.toString()))
    }

    /** Deletes the aahnik and (via ON DELETE CASCADE) all of its completion history. */
    fun deleteAahnik(id: Long) {
        writableDatabase.delete("aahnik", "id = ?", arrayOf(id.toString()))
    }

    fun getAllCompletions(): List<Completion> {
        val result = mutableListOf<Completion>()
        readableDatabase.rawQuery(
            "SELECT id, aahnik_id, day, completed_at, minute_of_day FROM completion ORDER BY day, minute_of_day",
            null
        ).use { c ->
            while (c.moveToNext()) {
                result += Completion(
                    id = c.getLong(0),
                    aahnikId = c.getLong(1),
                    day = LocalDate.parse(c.getString(2)),
                    completedAt = c.getLong(3),
                    minuteOfDay = c.getInt(4),
                )
            }
        }
        return result
    }

    /** Records (or replaces) today's completion of [aahnikId] at [at]. */
    fun markComplete(aahnikId: Long, at: LocalDateTime) {
        val values = ContentValues().apply {
            put("aahnik_id", aahnikId)
            put("day", at.toLocalDate().toString())
            put("completed_at", at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            put("minute_of_day", at.hour * 60 + at.minute)
        }
        writableDatabase.insertWithOnConflict("completion", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun unmark(aahnikId: Long, day: LocalDate) {
        writableDatabase.delete(
            "completion",
            "aahnik_id = ? AND day = ?",
            arrayOf(aahnikId.toString(), day.toString())
        )
    }

    companion object {
        private const val DB_NAME = "aahnik.db"
        private const val DB_VERSION = 1
    }
}
