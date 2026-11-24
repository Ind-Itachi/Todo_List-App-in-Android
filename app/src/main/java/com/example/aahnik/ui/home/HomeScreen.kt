package com.example.aahnik.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aahnik.data.Aahnik
import com.example.aahnik.data.Completion
import com.example.aahnik.data.TimeText
import com.example.aahnik.ui.AppState
import com.example.aahnik.ui.glass.GlassCard
import com.example.aahnik.ui.glass.LiquidCheck
import com.example.aahnik.ui.glass.ProgressOrb
import com.example.aahnik.ui.glass.glass
import com.example.aahnik.ui.theme.AahnikColors
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

@Composable
fun HomeScreen(
    state: AppState,
    is24Hour: Boolean,
    onTap: (Aahnik) -> Unit,
    onLongPress: (Aahnik) -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = state.todayDone
    val usual = remember(state.completions, state.today) {
        val since = state.today.minusDays(30)
        state.completions
            .filter { it.day.isAfter(since) && it.day.isBefore(state.today) }
            .groupBy { it.aahnikId }
            .mapValues { (_, list) -> list.map { it.cycleMinute }.average().roundToInt() }
    }
    val doneCount = state.aahniks.count { it.id in done }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.today.format(dateFormat).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = AahnikColors.TextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Today's Aahnik",
                        style = MaterialTheme.typography.displaySmall,
                        color = AahnikColors.TextPrimary,
                    )
                }
                ProgressOrb(done = doneCount, total = state.aahniks.size)
            }
        }

        item(key = "status") {
            StatusStrip(state, done, doneCount, is24Hour)
        }

        itemsIndexed(state.aahniks, key = { _, a -> a.id }) { index, aahnik ->
            EnterStagger(index, Modifier.animateItem()) {
                AahnikCard(
                    aahnik = aahnik,
                    completion = done[aahnik.id],
                    usualMinute = usual[aahnik.id],
                    is24Hour = is24Hour,
                    onClick = { onTap(aahnik) },
                    onLongClick = { onLongPress(aahnik) },
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(state: AppState, done: Map<Long, Completion>, doneCount: Int, is24Hour: Boolean) {
    val total = state.aahniks.size
    val message = when {
        !state.loaded -> ""
        total == 0 -> "Add the aahnik you follow each day with the + button. Tap one to mark it complete — the time is recorded for your analysis."
        doneCount == total -> {
            val last = done.values.maxOf { it.cycleMinute }
            "All complete — last one at ${TimeText.minute(last, is24Hour)}. 🙏"
        }
        doneCount == 0 -> "Tap an aahnik when you complete it. Long-press to edit or set a different time."
        else -> "${total - doneCount} remaining today"
    }
    if (message.isEmpty()) return
    val allDone = total > 0 && doneCount == total
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        fill = 0.06f,
        glow = if (allDone) AahnikColors.Saffron else Color.Transparent,
    ) {
        AnimatedContent(
            targetState = message,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically { it / 2 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically { -it / 2 })
            },
            label = "status",
        ) { text ->
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                color = if (allDone) AahnikColors.Gold else AahnikColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AahnikCard(
    aahnik: Aahnik,
    completion: Completion?,
    usualMinute: Int?,
    is24Hour: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDone = completion != null
    val rim by animateColorAsState(
        if (isDone) AahnikColors.Gold else Color.White,
        animationSpec = tween(500),
        label = "rim",
    )
    val glow by animateColorAsState(
        if (isDone) AahnikColors.Saffron else Color.Transparent,
        animationSpec = tween(700),
        label = "glow",
    )
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        rim = rim,
        glow = glow,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .glass(CircleShape, fill = 0.12f),
                contentAlignment = Alignment.Center,
            ) {
                Text(aahnik.icon, fontSize = 24.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = aahnik.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = AahnikColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = when {
                    completion != null -> "Completed at ${TimeText.minute(completion.minuteOfDay, is24Hour)}"
                    usualMinute != null -> "Usually around ${TimeText.minute(usualMinute, is24Hour)}"
                    else -> "Tap when complete"
                }
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        (fadeIn(tween(240, delayMillis = 120)) + slideInVertically { it }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically { -it })
                    },
                    label = "subtitle",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDone) AahnikColors.Gold else AahnikColors.TextMuted,
                        fontWeight = if (isDone) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            LiquidCheck(done = isDone)
        }
    }
}

/** Cards float up into place one after another the first time the list appears. */
@Composable
private fun EnterStagger(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * 55).toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 180f))
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 60.dp.toPx()
            val s = 0.94f + 0.06f * progress.value
            scaleX = s
            scaleY = s
        }
    ) { content() }
}
