package com.example.aahnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AahnikStatsTest {

    private val today = LocalDate.of(2026, 11, 24)
    private val puja = Aahnik(1, "Puja", "🪔", 0, today.minusDays(30))
    private val mala = Aahnik(2, "Mala", "📿", 1, today.minusDays(30))

    private fun done(a: Aahnik, daysAgo: Long, h: Int, m: Int = 0) =
        Completion(0, a.id, today.minusDays(daysAgo), 0, h * 60 + m)

    @Test
    fun averageEarliestLatestAndHours() {
        val data = listOf(done(puja, 2, 6, 0), done(puja, 1, 6, 30), done(puja, 0, 7, 0))
        val r = AahnikStats.analyze(listOf(puja), data, null, AnalysisRange.WEEK, today)
        assertEquals(390, r.averageMinute)
        assertEquals(360, r.earliestMinute)
        assertEquals(420, r.latestMinute)
        assertEquals(2, r.hourCounts[6])
        assertEquals(1, r.hourCounts[7])
        assertEquals(3, r.points.size)
    }

    @Test
    fun trendIsNegativeWhenGettingEarlier() {
        // 10 minutes earlier every day -> 70 minutes per week earlier.
        val data = (0L..5L).map { daysAgo -> done(puja, daysAgo, 6, 0).copy(minuteOfDay = 360 + (daysAgo * 10).toInt()) }
        val r = AahnikStats.analyze(listOf(puja), data, null, AnalysisRange.WEEK, today)
        assertEquals(-70.0, r.trendMinutesPerWeek!!, 0.001)
    }

    @Test
    fun trendNeedsEnoughData() {
        val r = AahnikStats.analyze(listOf(puja), listOf(done(puja, 0, 6)), null, AnalysisRange.WEEK, today)
        assertNull(r.trendMinutesPerWeek)
        assertNull(r.spreadMinutes)
    }

    @Test
    fun rangeExcludesOlderCompletions() {
        val data = listOf(done(puja, 20, 5), done(puja, 1, 6))
        val r = AahnikStats.analyze(listOf(puja), data, null, AnalysisRange.WEEK, today)
        assertEquals(1, r.points.size)
        assertEquals(today.minusDays(6), r.from)
    }

    @Test
    fun selectionFiltersToOneAahnik() {
        val data = listOf(done(puja, 0, 6), done(mala, 0, 21))
        val r = AahnikStats.analyze(listOf(puja, mala), data, mala.id, AnalysisRange.WEEK, today)
        assertTrue(r.points.all { it.aahnikId == mala.id })
        assertEquals(1, r.perAahnik.size)
    }

    @Test
    fun consistencyIgnoresDifferenceBetweenAahniks() {
        // Each aahnik is perfectly regular, just at different times of day.
        val data = (0L..3L).flatMap { listOf(done(puja, it, 6), done(mala, it, 21)) }
        val r = AahnikStats.analyze(listOf(puja, mala), data, null, AnalysisRange.WEEK, today)
        assertEquals(0, r.spreadMinutes)
    }

    @Test
    fun streakCountsOnlyDaysWhereEverythingWasDone() {
        val data = listOf(
            done(puja, 0, 6), // today: mala not done yet -> streak counts from yesterday
            done(puja, 1, 6), done(mala, 1, 21),
            done(puja, 2, 6), done(mala, 2, 21),
            done(puja, 3, 6), // missed mala -> break
            done(puja, 4, 6), done(mala, 4, 21),
        )
        val (current, best) = AahnikStats.streaks(listOf(puja, mala), data, today)
        assertEquals(2, current)
        assertEquals(2, best)
    }

    @Test
    fun completionRateUsesDaysSinceCreation() {
        val newOne = puja.copy(createdOn = today.minusDays(1))
        val data = listOf(done(newOne, 0, 6), done(newOne, 1, 6))
        val r = AahnikStats.analyze(listOf(newOne), data, null, AnalysisRange.MONTH, today)
        assertEquals(1f, r.completionRate!!, 0.0001f)
    }

    @Test
    fun timeFormatting() {
        assertEquals("6:05 AM", TimeText.minute(365, false))
        assertEquals("12:00 PM", TimeText.minute(720, false))
        assertEquals("12:00 AM", TimeText.minute(0, false))
        assertEquals("18:30", TimeText.minute(1110, true))
        assertEquals("1 h 5 min", TimeText.duration(65))
    }
}
