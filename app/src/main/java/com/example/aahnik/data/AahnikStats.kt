package com.example.aahnik.data

import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AnalysisRange(val label: String, val days: Int?) {
    WEEK("7D", 7),
    MONTH("30D", 30),
    QUARTER("90D", 90),
    ALL("All", null),
}

/** [cycleMinute] is on the 3 AM–3 AM timeline (180..1619); display it with [TimeText.minute]. */
data class TrendPoint(val aahnikId: Long, val day: LocalDate, val cycleMinute: Int)

data class AahnikSummary(
    val aahnik: Aahnik,
    val count: Int,
    val possibleDays: Int,
    val averageMinute: Int?,
) {
    val rate: Float get() = if (possibleDays == 0) 0f else (count.toFloat() / possibleDays).coerceAtMost(1f)
}

data class AnalysisResult(
    val from: LocalDate,
    val to: LocalDate,
    val points: List<TrendPoint>,
    /** Average / earliest / latest on the 3 AM–3 AM timeline; display with [TimeText.minute]. */
    val averageMinute: Int?,
    val earliestMinute: Int?,
    val latestMinute: Int?,
    /** Standard deviation (minutes) of completion time around each aahnik's own average. */
    val spreadMinutes: Int?,
    /** Linear trend of completion time in minutes per week. Negative = getting earlier. */
    val trendMinutesPerWeek: Double?,
    val completionRate: Float?,
    val currentStreak: Int,
    val bestStreak: Int,
    /**
     * Completions per hour of the aahnik day. Index 0 is 3–4 AM, index 23 is 2–3 AM
     * (clock hour = (index + [DayCycle.START_HOUR]) % 24).
     */
    val hourCounts: List<Int>,
    val perAahnik: List<AahnikSummary>,
)

/** Pure analysis functions; no Android dependencies so they are unit-testable on the JVM. */
object AahnikStats {

    fun analyze(
        aahniks: List<Aahnik>,
        completions: List<Completion>,
        selectedId: Long?,
        range: AnalysisRange,
        today: LocalDate,
    ): AnalysisResult {
        val selected = aahniks.filter { selectedId == null || it.id == selectedId }
            .ifEmpty { aahniks }
        val selectedIds = selected.map { it.id }.toSet()
        val relevant = completions.filter { it.aahnikId in selectedIds && !it.day.isAfter(today) }

        val earliestKnown = (relevant.map { it.day } + selected.map { it.createdOn }).minOrNull() ?: today
        val from = range.days?.let { today.minusDays((it - 1).toLong()) }
            ?: minOf(earliestKnown, today)

        val inRange = relevant.filter { !it.day.isBefore(from) }
            .sortedWith(compareBy({ it.day }, { it.cycleMinute }))
        val points = inRange.map { TrendPoint(it.aahnikId, it.day, it.cycleMinute) }
        val minutes = inRange.map { it.cycleMinute }

        // Residuals around each aahnik's own mean let us measure drift/consistency
        // even when "All" mixes a 5 AM puja with a 9 PM reading.
        val meanById = inRange.groupBy { it.aahnikId }
            .mapValues { (_, list) -> list.map { it.cycleMinute }.average() }
        val residuals = inRange.map { it.day.toEpochDay().toDouble() to (it.cycleMinute - meanById.getValue(it.aahnikId)) }

        val spread = if (residuals.size >= 2) {
            sqrt(residuals.sumOf { it.second * it.second } / (residuals.size - 1)).roundToInt()
        } else null

        val trend = slopePerDay(residuals)?.times(7)

        val perAahnik = selected.map { a ->
            val own = inRange.filter { it.aahnikId == a.id }
            val start = maxOf(from, a.createdOn)
            val possible = if (start.isAfter(today)) 0 else (today.toEpochDay() - start.toEpochDay() + 1).toInt()
            AahnikSummary(
                aahnik = a,
                count = own.size,
                possibleDays = maxOf(possible, own.size),
                averageMinute = own.takeIf { it.isNotEmpty() }?.map { it.cycleMinute }?.average()?.roundToInt(),
            )
        }
        val possibleTotal = perAahnik.sumOf { it.possibleDays }
        val rate = if (possibleTotal == 0) null else perAahnik.sumOf { it.count }.toFloat() / possibleTotal

        val hours = MutableList(24) { 0 }
        minutes.forEach { hours[((it - DayCycle.START_MINUTE) / 60).coerceIn(0, 23)]++ }

        val (current, best) = streaks(selected, relevant, today)

        return AnalysisResult(
            from = from,
            to = today,
            points = points,
            averageMinute = minutes.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            earliestMinute = minutes.minOrNull(),
            latestMinute = minutes.maxOrNull(),
            spreadMinutes = spread,
            trendMinutesPerWeek = trend,
            completionRate = rate,
            currentStreak = current,
            bestStreak = best,
            hourCounts = hours,
            perAahnik = perAahnik,
        )
    }

    /** Least-squares slope of y over x; null when there is not enough spread in x. */
    internal fun slopePerDay(xy: List<Pair<Double, Double>>): Double? {
        if (xy.size < 3) return null
        val mx = xy.sumOf { it.first } / xy.size
        val my = xy.sumOf { it.second } / xy.size
        val sxx = xy.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx == 0.0) return null
        val sxy = xy.sumOf { (it.first - mx) * (it.second - my) }
        return sxy / sxx
    }

    /**
     * A day counts toward a streak when every selected aahnik that existed on that day was completed.
     * The current streak does not break just because today is still in progress.
     */
    internal fun streaks(selected: List<Aahnik>, completions: List<Completion>, today: LocalDate): Pair<Int, Int> {
        if (selected.isEmpty()) return 0 to 0
        val doneByDay = completions.groupBy({ it.day }, { it.aahnikId }).mapValues { it.value.toSet() }
        val firstDay = (selected.map { it.createdOn } + completions.map { it.day }).min()

        fun complete(day: LocalDate): Boolean {
            val expected = selected.filter { !it.createdOn.isAfter(day) || doneByDay[day]?.contains(it.id) == true }
            if (expected.isEmpty()) return false
            val done = doneByDay[day] ?: return false
            return expected.all { it.id in done }
        }

        var best = 0
        var run = 0
        var day = firstDay
        while (!day.isAfter(today)) {
            run = if (complete(day)) run + 1 else 0
            best = maxOf(best, run)
            day = day.plusDays(1)
        }

        var current = 0
        var cursor = if (complete(today)) today else today.minusDays(1)
        while (!cursor.isBefore(firstDay) && complete(cursor)) {
            current++
            cursor = cursor.minusDays(1)
        }
        return current to best
    }
}

object TimeText {
    fun minute(minuteOfDay: Int, is24Hour: Boolean): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val h = m / 60
        val min = m % 60
        return if (is24Hour) {
            "%02d:%02d".format(h, min)
        } else {
            val h12 = if (h % 12 == 0) 12 else h % 12
            "%d:%02d %s".format(h12, min, if (h < 12) "AM" else "PM")
        }
    }

    fun hourShort(hour: Int, is24Hour: Boolean): String {
        val h = ((hour % 24) + 24) % 24
        return if (is24Hour) "%02d".format(h) else {
            val h12 = if (h % 12 == 0) 12 else h % 12
            "$h12${if (h < 12) "a" else "p"}"
        }
    }

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
