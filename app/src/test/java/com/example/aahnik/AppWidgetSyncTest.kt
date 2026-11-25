package com.example.aahnik

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aahnik.data.AahnikDatabase
import com.example.aahnik.data.DayCycle
import com.example.aahnik.ui.AahnikViewModel
import com.example.aahnik.widget.loadWidgetData
import com.example.aahnik.widget.toggleFromWidget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Verifies that the app screen and the home-screen widget stay in sync in both directions,
 * using the real database, the real ViewModel and the widget's tap handler.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppWidgetSyncTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        AahnikDatabase.resetForTests()
        app.deleteDatabase("aahnik.db")
    }

    @After
    fun tearDown() {
        AahnikDatabase.resetForTests()
    }

    /** Runs main-thread work (ViewModel coroutines) until [condition] holds. */
    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        fail("Timed out waiting until: $what")
    }

    @Test
    fun widgetTapsShowUpInTheOpenApp() = runBlocking {
        val vm = AahnikViewModel(app)
        val id = AahnikDatabase.get(app).addAahnik("Puja", "🪔", DayCycle.today())
        waitUntil("the app lists the new aahnik") { vm.state.value.aahniks.any { it.id == id } }

        // Widget tap on an unfinished aahnik marks it done...
        assertTrue(toggleFromWidget(app, id))
        waitUntil("the app shows the widget's completion") { vm.state.value.todayDone.containsKey(id) }

        // ...and tapping it again undoes it.
        assertFalse(toggleFromWidget(app, id))
        waitUntil("the app shows the widget's undo") { !vm.state.value.todayDone.containsKey(id) }
    }

    @Test
    fun appActionsShowUpInTheWidget() {
        val vm = AahnikViewModel(app)
        vm.addAahnik("Mala", "📿")
        waitUntil("the widget lists the new aahnik") { loadWidgetData(app).items.size == 1 }
        waitUntil("the app lists the new aahnik") { vm.state.value.aahniks.size == 1 }
        val aahnik = vm.state.value.aahniks.single()

        vm.markDoneNow(aahnik)
        waitUntil("the widget shows the app's completion") { loadWidgetData(app).items.single().completion != null }

        vm.undoToday(aahnik)
        waitUntil("the widget shows the app's undo") { loadWidgetData(app).items.single().completion == null }

        vm.updateAahnik(aahnik, "Mala (108)", "📿")
        waitUntil("the widget shows the rename") { loadWidgetData(app).items.single().aahnik.name == "Mala (108)" }

        vm.deleteAahnik(aahnik)
        waitUntil("the widget drops the deleted aahnik") { loadWidgetData(app).items.isEmpty() }
    }

    @Test
    fun everyWriteSignalsAChange() {
        val db = AahnikDatabase.get(app)
        var last = AahnikDatabase.changes.value
        fun assertBumped(action: String) {
            val now = AahnikDatabase.changes.value
            assertTrue("$action should signal a change", now > last)
            last = now
        }
        val id = db.addAahnik("Puja", "🪔", DayCycle.today()); assertBumped("add")
        db.updateAahnik(id, "Nitya puja", "🪔"); assertBumped("rename")
        db.markComplete(id, LocalDateTime.now()); assertBumped("mark done")
        db.unmark(id, DayCycle.today()); assertBumped("undo")
        db.toggleToday(id, LocalDateTime.now()); assertBumped("widget toggle")
        db.deleteAahnik(id); assertBumped("delete")
    }

    @Test
    fun widgetToggleUsesThe3amDay() {
        val db = AahnikDatabase.get(app)
        val id = db.addAahnik("Arti", "🔔", LocalDate.of(2026, 11, 20))
        // 1:30 AM on the 25th belongs to the aahnik day of the 24th.
        assertTrue(db.toggleToday(id, LocalDateTime.of(2026, 11, 25, 1, 30)))
        val c = db.getAllCompletions().single()
        assertEquals(LocalDate.of(2026, 11, 24), c.day)
        assertEquals(90, c.minuteOfDay)
        // Tapping again in the same aahnik day (2:10 AM) undoes it.
        assertFalse(db.toggleToday(id, LocalDateTime.of(2026, 11, 25, 2, 10)))
        assertTrue(db.getAllCompletions().isEmpty())
    }

    @Test
    fun homeAndWidgetShowOnlyTheCurrentCycle() {
        val db = AahnikDatabase.get(app)
        val cycle = DayCycle.today()
        val puja = db.addAahnik("Puja", "🪔", cycle.minusDays(5))
        val mala = db.addAahnik("Mala", "📿", cycle.minusDays(5))
        // Done in the previous cycle (9 PM the day before): must NOT show as done now.
        db.markComplete(mala, cycle.minusDays(1).atTime(21, 0))
        // Done in the current cycle (6 AM): must show as done.
        db.markComplete(puja, cycle.atTime(6, 0))

        val vm = AahnikViewModel(app)
        waitUntil("the app has loaded") { vm.state.value.loaded && vm.state.value.aahniks.size == 2 }
        assertEquals(cycle, vm.state.value.today)
        assertEquals(setOf(puja), vm.state.value.todayDone.keys)

        val widget = loadWidgetData(app)
        assertEquals(cycle, widget.today)
        assertEquals(listOf(puja), widget.items.filter { it.completion != null }.map { it.aahnik.id })
        assertEquals(1, widget.done)
    }

    @Test
    fun afterMidnightUntil3amIsStillTheSameCycle() {
        val db = AahnikDatabase.get(app)
        val cycle = DayCycle.today()
        val katha = db.addAahnik("Katha", "🎵", cycle.minusDays(5))
        // 12:18 AM on the next calendar date belongs to the current cycle.
        db.markComplete(katha, cycle.plusDays(1).atTime(0, 18))
        // 3:05 AM on the next calendar date is the NEXT cycle: not shown yet.
        val chesta = db.addAahnik("Chesta", "🌙", cycle.minusDays(5))
        db.markComplete(chesta, cycle.plusDays(1).atTime(3, 5))

        val vm = AahnikViewModel(app)
        waitUntil("the app has loaded") { vm.state.value.loaded && vm.state.value.aahniks.size == 2 }
        assertEquals(setOf(katha), vm.state.value.todayDone.keys)
        assertEquals(listOf(katha), loadWidgetData(app).items.filter { it.completion != null }.map { it.aahnik.id })
    }
}
