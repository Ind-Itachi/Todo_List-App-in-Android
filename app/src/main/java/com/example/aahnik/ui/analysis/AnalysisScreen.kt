package com.example.aahnik.ui.analysis

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aahnik.data.AahnikStats
import com.example.aahnik.data.AnalysisRange
import com.example.aahnik.data.AnalysisResult
import com.example.aahnik.data.TimeText
import com.example.aahnik.ui.AppState
import com.example.aahnik.ui.glass.GlassCard
import com.example.aahnik.ui.glass.LiquidSegmented
import com.example.aahnik.ui.glass.glass
import com.example.aahnik.ui.theme.AahnikColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AnalysisScreen(state: AppState, is24Hour: Boolean, modifier: Modifier = Modifier) {
    var rangeIndex by rememberSaveable { mutableStateOf(1) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    if (selectedId != null && state.aahniks.none { it.id == selectedId }) selectedId = null
    val range = AnalysisRange.entries[rangeIndex]

    val result = remember(state.aahniks, state.completions, selectedId, range, state.today) {
        AahnikStats.analyze(state.aahniks, state.completions, selectedId, range, state.today)
    }
    val series = remember(state.aahniks) {
        state.aahniks.associate { it.id to SeriesInfo(it.id, it.name, it.icon, AahnikColors.series(it.position)) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("INSIGHTS", style = MaterialTheme.typography.labelMedium, color = AahnikColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                Text("Analysis", style = MaterialTheme.typography.displaySmall, color = AahnikColors.TextPrimary)
            }
        }

        // Filters live in one row group above the charts.
        item(key = "range") {
            LiquidSegmented(
                count = AnalysisRange.entries.size,
                selected = rangeIndex,
                onSelect = { rangeIndex = it },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
            ) { i, isSelected ->
                Text(
                    AnalysisRange.entries[i].label,
                    color = if (isSelected) AahnikColors.TextPrimary else AahnikColors.TextMuted,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }

        if (state.aahniks.size > 1) {
            item(key = "chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(label = "All", dot = null, selected = selectedId == null) { selectedId = null }
                    }
                    items(state.aahniks, key = { it.id }) { a ->
                        FilterChip(
                            label = "${a.icon} ${a.name}",
                            dot = series[a.id]?.color,
                            selected = selectedId == a.id,
                        ) { selectedId = a.id }
                    }
                }
            }
        }

        if (result.points.isEmpty()) {
            item(key = "empty") {
                GlassCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), fill = 0.07f) {
                    Text(
                        text = if (state.aahniks.isEmpty())
                            "Add aahnik on the Today tab and mark them complete. Your completion times will show up here."
                        else
                            "No completions in this range yet. Mark aahnik complete on the Today tab to see when you usually do them.",
                        modifier = Modifier.padding(20.dp),
                        color = AahnikColors.TextSecondary,
                    )
                }
            }
            return@LazyColumn
        }

        item(key = "tiles") {
            StatTiles(result, is24Hour, Modifier.padding(horizontal = 20.dp))
        }

        item(key = "trend") {
            val visibleSeries = result.points.map { it.aahnikId }.distinct()
            ChartCard(
                title = "Completion time trend",
                subtitle = "Each dot is one completion. Higher = earlier in the day." +
                    if (visibleSeries.size == 1) " Dashed line = average, dotted = trend." else "",
            ) {
                TrendChart(
                    points = result.points,
                    series = series,
                    from = result.from,
                    to = result.to,
                    showAverageAndTrend = visibleSeries.size == 1,
                    is24Hour = is24Hour,
                )
                if (visibleSeries.size > 1) {
                    Legend(visibleSeries.mapNotNull { series[it] })
                }
            }
        }

        item(key = "hours") {
            val peak = result.hourCounts.indices.maxByOrNull { result.hourCounts[it] }
            ChartCard(
                title = "What time you complete",
                subtitle = peak?.let {
                    "Most often between ${TimeText.minute(it * 60, is24Hour)} and ${TimeText.minute((it + 1) * 60, is24Hour)}. Tap a bar for details."
                } ?: "",
            ) {
                HourHistogram(result.hourCounts, is24Hour)
            }
        }

        if (result.perAahnik.size > 1) {
            item(key = "per-aahnik") {
                ChartCard(
                    title = "Per aahnik",
                    subtitle = "Average completion time and how many days you completed it in this range.",
                ) {
                    PerAahnikTable(result, series, is24Hour)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    val rim by animateColorAsState(if (selected) AahnikColors.Gold else Color.White, label = "chip-rim")
    GlassCard(
        shape = CircleShape,
        fill = if (selected) 0.22f else 0.06f,
        rim = rim,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                color = if (selected) AahnikColors.TextPrimary else AahnikColors.TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatTiles(result: AnalysisResult, is24Hour: Boolean, modifier: Modifier = Modifier) {
    val avg = result.averageMinute?.let { TimeText.minute(it, is24Hour) } ?: "—"
    val range = if (result.earliestMinute != null && result.latestMinute != null)
        "${TimeText.minute(result.earliestMinute, is24Hour)} – ${TimeText.minute(result.latestMinute, is24Hour)}"
    else "range"

    val trend = result.trendMinutesPerWeek
    val (trendValue, trendCaption) = when {
        trend == null -> "—" to "needs a few more days"
        abs(trend) < 1.0 -> "Steady" to "no drift in this range"
        trend < 0 -> "${abs(trend).roundToInt()} min earlier" to "per week"
        else -> "${trend.roundToInt()} min later" to "per week"
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Average time", avg, range, Modifier.weight(1f))
            StatTile(
                "Consistency",
                result.spreadMinutes?.let { "± ${TimeText.duration(it)}" } ?: "—",
                "typical variation",
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Trend", trendValue, trendCaption, Modifier.weight(1f))
            StatTile(
                "Streak",
                "${result.currentStreak} day${if (result.currentStreak == 1) "" else "s"}",
                "best ${result.bestStreak}" + (result.completionRate?.let { " · ${(it * 100).roundToInt()}% done" } ?: ""),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(24.dp), fill = 0.08f) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = AahnikColors.TextMuted)
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.85f)) togetherWith
                        fadeOut(tween(120))
                },
                label = "tile-$label",
            ) { v ->
                Text(
                    v,
                    color = AahnikColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(caption, color = AahnikColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        fill = 0.08f,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AahnikColors.TextPrimary)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AahnikColors.TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(items: List<SeriesInfo>) {
    FlowRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                Spacer(Modifier.width(6.dp))
                Text("${s.icon} ${s.name}", color = AahnikColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PerAahnikTable(result: AnalysisResult, series: Map<Long, SeriesInfo>, is24Hour: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        result.perAahnik.forEach { s ->
            val rate by animateFloatAsState(s.rate, spring(dampingRatio = 0.7f, stiffness = 80f), label = "rate")
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(series[s.aahnik.id]?.color ?: Color.White))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${s.aahnik.icon} ${s.aahnik.name}",
                        modifier = Modifier.weight(1f),
                        color = AahnikColors.TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        s.averageMinute?.let { "avg ${TimeText.minute(it, is24Hour)}" } ?: "not yet",
                        color = AahnikColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .glass(CircleShape, fill = 0.04f, rimAlpha = 0.4f)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(rate.coerceIn(0f, 1f))
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(AahnikColors.SaffronDeep, AahnikColors.Gold)))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${s.count}/${s.possibleDays} days",
                        color = AahnikColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
