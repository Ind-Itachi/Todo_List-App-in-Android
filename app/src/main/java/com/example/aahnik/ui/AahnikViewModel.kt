package com.example.aahnik.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aahnik.data.Aahnik
import com.example.aahnik.data.AahnikDatabase
import com.example.aahnik.data.Completion
import com.example.aahnik.data.DayCycle
import com.example.aahnik.widget.AahnikWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class AppState(
    val loaded: Boolean = false,
    /** The current aahnik day (3 AM–3 AM). */
    val today: LocalDate = DayCycle.today(),
    val aahniks: List<Aahnik> = emptyList(),
    val completions: List<Completion> = emptyList(),
) {
    val todayDone: Map<Long, Completion>
        get() = completions.filter { it.day == today }.associateBy { it.aahnikId }
}

class AahnikViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AahnikDatabase.get(app)
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        // Reload whenever anything is written — by this screen or by the home-screen widget.
        viewModelScope.launch {
            AahnikDatabase.changes.collect { reload() }
        }
        // Roll over to the new aahnik day at 3 AM even if the app stays open.
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                delay(Duration.between(now, DayCycle.nextStart(now)).toMillis() + 1_000)
                reload()
            }
        }
    }

    /** Reloads everything from the database (called on resume; also picks up the 3 AM rollover). */
    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun markDoneNow(aahnik: Aahnik) = write {
        db.markComplete(aahnik.id, LocalDateTime.now())
    }

    fun markDoneAt(aahnik: Aahnik, time: LocalTime) = write {
        db.markComplete(aahnik.id, DayCycle.dateTimeFor(DayCycle.today(), time))
    }

    fun undoToday(aahnik: Aahnik) = write {
        db.unmark(aahnik.id, DayCycle.today())
    }

    fun addAahnik(name: String, icon: String) = write {
        db.addAahnik(name.trim(), icon, DayCycle.today())
    }

    fun updateAahnik(aahnik: Aahnik, name: String, icon: String) = write {
        db.updateAahnik(aahnik.id, name.trim(), icon)
    }

    fun deleteAahnik(aahnik: Aahnik) = write {
        db.deleteAahnik(aahnik.id)
    }

    private fun write(block: () -> Unit) {
        viewModelScope.launch {
            writeLock.withLock {
                withContext(Dispatchers.IO) { block() }
            }
            // The database change signal reloads this screen; tell the widget too.
            // A widget problem must never break the app.
            runCatching { AahnikWidget.refreshAll(getApplication()) }
        }
    }

    private suspend fun reload() {
        val (aahniks, completions) = withContext(Dispatchers.IO) {
            db.getAahniks() to db.getAllCompletions()
        }
        _state.update {
            it.copy(loaded = true, today = DayCycle.today(), aahniks = aahniks, completions = completions)
        }
    }
}
