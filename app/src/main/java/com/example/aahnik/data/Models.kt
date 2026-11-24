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

/** A single "done" mark: which aahnik, which day, and the time of day it was completed. */
data class Completion(
    val id: Long,
    val aahnikId: Long,
    val day: LocalDate,
    val completedAt: Long,
    /** Minutes since local midnight (0..1439) when the aahnik was marked complete. */
    val minuteOfDay: Int,
)
