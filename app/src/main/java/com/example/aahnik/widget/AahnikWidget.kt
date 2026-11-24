package com.example.aahnik.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.aahnik.MainActivity
import com.example.aahnik.R
import com.example.aahnik.data.Aahnik
import com.example.aahnik.data.AahnikDatabase
import com.example.aahnik.data.Completion
import com.example.aahnik.data.DayCycle
import com.example.aahnik.data.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class WidgetItem(val aahnik: Aahnik, val completion: Completion?)

private data class WidgetData(val today: LocalDate, val items: List<WidgetItem>, val is24Hour: Boolean) {
    val done get() = items.count { it.completion != null }
}

private val White = ColorProvider(Color.White)
private val Muted = ColorProvider(Color(0x99FFFFFF))
private val Gold = ColorProvider(Color(0xFFFFD86B))
private val headerDate = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

/** Home-screen widget: today's aahnik list. Tap one to mark it done now. */
class AahnikWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { load(context) }
        scheduleDayRollover(context)
        provideContent { WidgetContent(data) }
    }

    private fun load(context: Context): WidgetData {
        val db = AahnikDatabase.get(context)
        val today = DayCycle.today()
        val done = db.getCompletionsForDay(today).associateBy { it.aahnikId }
        return WidgetData(
            today = today,
            items = db.getAahniks().map { WidgetItem(it, done[it.id]) },
            is24Hour = DateFormat.is24HourFormat(context),
        )
    }

    companion object {
        /** Redraws every placed widget (call after the app changes data). */
        suspend fun refreshAll(context: Context) {
            AahnikWidget().updateAll(context)
        }

        /** Wakes the widget just after 3 AM so it switches to the new aahnik day. */
        private fun scheduleDayRollover(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AahnikWidgetReceiver::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, AahnikWidgetReceiver::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            val pending = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val at = DayCycle.nextStart(LocalDateTime.now()).plusSeconds(30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at, pending)
        }
    }
}

class AahnikWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AahnikWidget()
}

private val AahnikIdKey = ActionParameters.Key<Long>("aahnik_id")

/** Tap on an unfinished aahnik: record the current time, then redraw. */
class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[AahnikIdKey] ?: return
        withContext(Dispatchers.IO) {
            AahnikDatabase.get(context).markCompleteIfAbsent(id, LocalDateTime.now())
        }
        AahnikWidget().updateAll(context)
    }
}

@Composable
private fun WidgetContent(data: WidgetData) {
    val openApp = actionStartActivity<MainActivity>()
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_glass_bg))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(openApp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("AAHNIK", style = TextStyle(color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                    Text(
                        data.today.format(headerDate),
                        style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    "${data.done}/${data.items.size}",
                    style = TextStyle(
                        color = if (data.items.isNotEmpty() && data.done == data.items.size) Gold else White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.height(8.dp))

            if (data.items.isEmpty()) {
                Text(
                    "Open the app and tap + to add your aahnik.",
                    modifier = GlanceModifier.padding(4.dp).clickable(openApp),
                    style = TextStyle(color = Muted, fontSize = 13.sp),
                )
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(data.items, itemId = { it.aahnik.id }) { item ->
                        Column {
                            WidgetRow(item, data.is24Hour)
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(item: WidgetItem, is24Hour: Boolean) {
    val done = item.completion != null
    val action = if (done) {
        actionStartActivity<MainActivity>()
    } else {
        actionRunCallback<MarkDoneAction>(actionParametersOf(AahnikIdKey to item.aahnik.id))
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(if (done) R.drawable.widget_item_done_bg else R.drawable.widget_item_bg))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.aahnik.icon, style = TextStyle(fontSize = 18.sp))
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                item.aahnik.name,
                maxLines = 1,
                style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            if (item.completion != null) {
                Text(
                    "Done at ${TimeText.minute(item.completion.minuteOfDay, is24Hour)}",
                    style = TextStyle(color = Gold, fontSize = 11.sp),
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Image(
            provider = ImageProvider(if (done) R.drawable.widget_check_done else R.drawable.widget_check_empty),
            contentDescription = if (done) "Done" else "Mark done",
            modifier = GlanceModifier.size(24.dp),
        )
    }
}
