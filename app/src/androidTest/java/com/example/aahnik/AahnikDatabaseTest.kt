package com.example.aahnik

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aahnik.data.AahnikDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class AahnikDatabaseTest {
    @Test
    fun markCompleteRecordsTimeAndReplacesSameDay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("aahnik.db")
        val db = AahnikDatabase(context)
        val day = LocalDate.of(2026, 11, 24)
        val id = db.addAahnik("Puja", "🪔", day)

        db.markComplete(id, LocalDateTime.of(2026, 11, 24, 6, 15))
        db.markComplete(id, LocalDateTime.of(2026, 11, 24, 6, 40))

        val completions = db.getAllCompletions()
        assertEquals(1, completions.size)
        assertEquals(6 * 60 + 40, completions.single().minuteOfDay)

        db.deleteAahnik(id)
        assertEquals(0, db.getAllCompletions().size)
        db.close()
        context.deleteDatabase("aahnik.db")
    }
}
