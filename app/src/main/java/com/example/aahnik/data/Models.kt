package com.example.aahnik.data

import java.time.LocalDate

/** One daily practice the user wants to track (e.g. puja, mala, reading). */
data class Aahnik(
    val id: Long,
    val name: String,
    val icon: String,
    val position: Int,
    val createdOn: LocalDate,
)

/** A single "done" mark: which aahnik, which aahnik day (3 AM–3 AM), and the time it was completed. */
data class Completion(
    val id: Long,
    val aahnikId: Long,
    val day: LocalDate,
    val completedAt: Long,
    /** Clock time (minutes since midnight, 0..1439) when the aahnik was marked complete. */
    val minuteOfDay: Int,
) {
    /** Time on the 3 AM–3 AM aahnik-day timeline (180..1619), so 1 AM comes after 11 PM. */
    val cycleMinute: Int get() = DayCycle.cycleMinute(minuteOfDay)
}
