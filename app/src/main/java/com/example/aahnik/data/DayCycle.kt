package com.example.aahnik.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The aahnik day runs from 3:00 AM to 3:00 AM, not midnight to midnight.
 * Something completed at 1:30 AM belongs to the previous day.
 */
object DayCycle {
    const val START_HOUR = 3
    const val START_MINUTE = START_HOUR * 60

    /** The aahnik day that [at] belongs to. */
    fun dayOf(at: LocalDateTime): LocalDate = at.minusHours(START_HOUR.toLong()).toLocalDate()

    fun today(): LocalDate = dayOf(LocalDateTime.now())

    /**
     * Clock minute (0..1439) mapped onto the aahnik day's timeline (180..1619), so that
     * 1:00 AM sorts and averages *after* 11:00 PM. Display with `% 1440`.
     */
    fun cycleMinute(clockMinute: Int): Int =
        if (clockMinute < START_MINUTE) clockMinute + 1440 else clockMinute

    /** The real date-time for [time] within aahnik day [day] (times before 3 AM fall on the next calendar date). */
    fun dateTimeFor(day: LocalDate, time: LocalTime): LocalDateTime =
        if (time.hour < START_HOUR) day.plusDays(1).atTime(time) else day.atTime(time)

    /** When the next aahnik day starts after [now]. */
    fun nextStart(now: LocalDateTime): LocalDateTime {
        val start = now.toLocalDate().atTime(START_HOUR, 0)
        return if (now.isBefore(start)) start else start.plusDays(1)
    }
}
