package com.example.aahnik.ui.analysis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aahnik.data.TimeText
import com.example.aahnik.data.TrendPoint
import com.example.aahnik.ui.theme.AahnikColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

data class SeriesInfo(val id: Long, val name: String, val icon: String, val color: Color)

private val GlassSurface = Color(0xFF1C1A3A)
private val GridColor = Color.White.copy(alpha = 0.08f)
private val dayLabel = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val tooltipDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

private class TrendFrame(
    val size: Size,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val from: LocalDate,
    val to: LocalDate,
    val minMinute: Int,
    val maxMinute: Int,
) {
    val plotWidth = size.width - left - right
    val plotHeight = size.height - top - bottom

    fun x(day: LocalDate): Float {
        val span = to.toEpochDay() - from.toEpochDay()
        if (span <= 0) return left + plotWidth / 2f
        return left + plotWidth * (day.toEpochDay() - from.toEpochDay()) / span.toFloat()
    }

    /** Earlier times sit higher on the chart, like a day planner. */
    fun y(minute: Float): Float = top + plotHeight * (minute - minMinute) / (maxMinute - minMinute).toFloat()
}

internal fun minuteRange(points: List<TrendPoint>): Pair<Int, Int> {
    if (points.isEmpty()) return 300 to 540
    var lo = (floor((points.minOf { it.minuteOfDay } - 15) / 60.0) * 60).toInt().coerceAtLeast(0)
    var hi = (ceil((points.maxOf { it.minuteOfDay } + 15) / 60.0) * 60).toInt().coerceAtMost(1440)
    if (hi - lo < 120) {
        hi = (lo + 120).coerceAtMost(1440)
        lo = (hi - 120).coerceAtLeast(0)
    }
    return lo to hi
}

private fun gridStep(span: Int) = when {
    span <= 240 -> 60
    span <= 480 -> 120
    span <= 720 -> 180
    else -> 240
}

/**
 * Time-of-day trend: one dot per completion, x = day, y = time of day.
 * With a single aahnik it also shows the average line and the fitted trend.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    series: Map<Long, SeriesInfo>,
    from: LocalDate,
    to: LocalDate,
    showAverageAndTrend: Boolean,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points, from, to) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }
    var selected by remember(points) { mutableStateOf<TrendPoint?>(null) }
    val (minMinute, maxMinute) = remember(points) { minuteRange(points) }
    val byDay = remember(points) { points.groupBy { it.aahnikId }.mapValues { (_, l) -> l.sortedBy { it.day } } }

    fun frameFor(size: Size, density: Float) = TrendFrame(
        size = size,
        left = 46f * density,
        top = 12f * density,
        right = 10f * density,
        bottom = 24f * density,
        from = from,
        to = to,
        minMinute = minMinute,
        maxMinute = maxMinute,
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(points, from, to) {
                detectTapGestures { tap ->
                    val frame = frameFor(Size(size.width.toFloat(), size.height.toFloat()), density)
                    val nearest = points.minByOrNull {
                        hypot(frame.x(it.day) - tap.x, frame.y(it.minuteOfDay.toFloat()) - tap.y)
                    }
                    selected = nearest?.takeIf {
                        hypot(frame.x(it.day) - tap.x, frame.y(it.minuteOfDay.toFloat()) - tap.y) < 32.dp.toPx()
                    }?.takeIf { it != selected }
                }
            }
    ) {
        val frame = frameFor(size, density)
        val labelStyle = TextStyle(color = AahnikColors.TextMuted, fontSize = 10.sp)

        // Horizontal hour grid with time labels.
        val step = gridStep(maxMinute - minMinute)
        var m = minMinute
        while (m <= maxMinute) {
            val y = frame.y(m.toFloat())
            drawLine(GridColor, Offset(frame.left, y), Offset(size.width - frame.right, y), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(TimeText.minute(m, is24Hour), labelStyle)
            drawText(label, topLeft = Offset(frame.left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
            m += step
        }

        // Date labels along the bottom.
        val days = (to.toEpochDay() - from.toEpochDay()).toInt()
        val labelDays = when {
            days <= 0 -> listOf(from)
            days < 4 -> (0..days).map { from.plusDays(it.toLong()) }
            else -> listOf(from, from.plusDays((days / 2).toLong()), to)
        }
        labelDays.forEachIndexed { i, d ->
            val label = measurer.measure(d.format(dayLabel), labelStyle)
            val cx = frame.x(d)
            val x = when {
                labelDays.size > 1 && i == 0 -> cx
                labelDays.size > 1 && i == labelDays.lastIndex -> cx - label.size.width
                else -> cx - label.size.width / 2f
            }.coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
            drawText(label, topLeft = Offset(x, size.height - label.size.height))
        }

        val reveal = frame.left + frame.plotWidth * progress.value + 8.dp.toPx()

        if (showAverageAndTrend && points.isNotEmpty()) {
            drawAverageAndTrend(frame, points, measurer, reveal)
        }

        val dense = points.size > 90
        val radius = if (dense) 3.dp.toPx() else 4.dp.toPx()
        clipRect(right = reveal) {
            byDay.forEach { (id, list) ->
                val color = series[id]?.color ?: AahnikColors.Saffron
                if (list.size > 1) {
                    val path = Path()
                    list.forEachIndexed { i, p ->
                        val o = Offset(frame.x(p.day), frame.y(p.minuteOfDay.toFloat()))
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(
                        path,
                        color = color.copy(alpha = 0.75f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                list.forEach { p ->
                    val o = Offset(frame.x(p.day), frame.y(p.minuteOfDay.toFloat()))
                    drawCircle(GlassSurface, radius = radius + 2.dp.toPx(), center = o)
                    drawCircle(color, radius = radius, center = o)
                }
            }
        }

        selected?.let { p ->
            val o = Offset(frame.x(p.day), frame.y(p.minuteOfDay.toFloat()))
            val info = series[p.aahnikId]
            drawCircle(Color.White, radius = radius + 3.dp.toPx(), center = o, style = Stroke(2.dp.toPx()))
            val text = buildString {
                if (series.size > 1 && info != null) append("${info.icon} ${info.name}\n")
                append(p.day.format(tooltipDay))
                append(" · ")
                append(TimeText.minute(p.minuteOfDay, is24Hour))
            }
            drawTooltip(measurer, text, o)
        }
    }
}

private fun DrawScope.drawAverageAndTrend(
    frame: TrendFrame,
    points: List<TrendPoint>,
    measurer: TextMeasurer,
    reveal: Float,
) {
    val avg = points.map { it.minuteOfDay }.average().toFloat()
    val yAvg = frame.y(avg)
    clipRect(right = reveal) {
        drawLine(
            Color.White.copy(alpha = 0.45f),
            Offset(frame.left, yAvg),
            Offset(size.width - frame.right, yAvg),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
    val avgLabel = measurer.measure("avg", TextStyle(color = AahnikColors.TextSecondary, fontSize = 10.sp))
    drawText(avgLabel, topLeft = Offset(size.width - frame.right - avgLabel.size.width, yAvg - avgLabel.size.height - 2.dp.toPx()))

    // Least-squares trend line across the range.
    if (points.size >= 3 && points.map { it.day }.distinct().size >= 2) {
        val xs = points.map { it.day.toEpochDay().toDouble() }
        val ys = points.map { it.minuteOfDay.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
        fun at(day: LocalDate) = (my + slope * (day.toEpochDay() - mx)).toFloat()
        val start = Offset(frame.x(frame.from), frame.y(at(frame.from)))
        val end = Offset(frame.x(frame.to), frame.y(at(frame.to)))
        clipRect(left = frame.left, top = frame.top, right = reveal, bottom = frame.top + frame.plotHeight) {
            drawLine(
                AahnikColors.Gold.copy(alpha = 0.85f),
                start,
                end,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
            )
        }
    }
}

private fun DrawScope.drawTooltip(measurer: TextMeasurer, text: String, anchor: Offset) {
    val padH = 10.dp.toPx()
    val layout = measurer.measure(
        text,
        TextStyle(color = AahnikColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        constraints = Constraints(maxWidth = (size.width - padH * 2).toInt().coerceAtLeast(1)),
    )
    val padV = 7.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val x = (anchor.x - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    val above = anchor.y - h - 12.dp.toPx()
    val y = if (above >= 0f) above else anchor.y + 12.dp.toPx()
    drawRoundRect(
        color = Color(0xF21C1A3A),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(12.dp.toPx()),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.35f),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(12.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
    drawText(layout, topLeft = Offset(x + padH, y + padV))
}

/** Hour-of-day histogram: how often you complete in each hour. */
@Composable
fun HourHistogram(
    hourCounts: List<Int>,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(hourCounts) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    var selected by remember(hourCounts) { mutableStateOf<Int?>(null) }

    val window = remember(hourCounts) {
        val first = hourCounts.indexOfFirst { it > 0 }
        val last = hourCounts.indexOfLast { it > 0 }
        if (first < 0) 0..23 else {
            var lo = (first - 1).coerceAtLeast(0)
            var hi = (last + 1).coerceAtMost(23)
            while (hi - lo + 1 < 8) {
                if (lo > 0) lo--
                if (hi - lo + 1 < 8 && hi < 23) hi++
            }
            lo..hi
        }
    }
    val hours = window.toList()
    val maxCount = (hourCounts.maxOrNull() ?: 0).coerceAtLeast(1)
    val peak = hourCounts.indices.maxByOrNull { hourCounts[it] }?.takeIf { hourCounts[it] > 0 }

    fun barGeometry(width: Float, density: Float): Triple<Float, Float, Float> {
        val left = 4f * density
        val slot = (width - left * 2) / hours.size
        val gap = 2f * density
        return Triple(left, slot, gap)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(hourCounts) {
                detectTapGestures { tap ->
                    val (left, slot, _) = barGeometry(size.width.toFloat(), density)
                    val i = ((tap.x - left) / slot).toInt()
                    val hour = hours.getOrNull(i)
                    selected = if (hour == null || hour == selected || hourCounts[hour] == 0) null else hour
                }
            }
    ) {
        val (left, slot, gap) = barGeometry(size.width, density)
        val labelStyle = TextStyle(color = AahnikColors.TextMuted, fontSize = 10.sp)
        val bottom = size.height - 20.dp.toPx()
        val top = 22.dp.toPx()
        val plotH = bottom - top

        drawLine(GridColor, Offset(left, bottom), Offset(size.width - left, bottom), strokeWidth = 1.dp.toPx())

        val labelEvery = if (hours.size > 12) 3 else 2
        hours.forEachIndexed { i, hour ->
            val count = hourCounts[hour]
            val x = left + slot * i + gap / 2
            val w = slot - gap
            // Bars rise one after another.
            val local = ((progress.value * (hours.size + 6) - i) / 6f).coerceIn(0f, 1f)
            val eased = FastOutSlowInEasing.transform(local)
            if (count > 0) {
                val h = plotH * count / maxCount * eased
                val r = minOf(4.dp.toPx(), w / 2)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = bottom - h, right = x + w, bottom = bottom,
                            topLeftCornerRadius = CornerRadius(r),
                            topRightCornerRadius = CornerRadius(r),
                            bottomLeftCornerRadius = CornerRadius.Zero,
                            bottomRightCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                val isSelected = selected == hour
                drawPath(
                    path,
                    Brush.verticalGradient(
                        listOf(AahnikColors.Gold, AahnikColors.SaffronDeep),
                        startY = bottom - plotH,
                        endY = bottom,
                    ),
                    alpha = if (selected == null || isSelected) 1f else 0.45f,
                )
                if ((hour == peak && selected == null) || isSelected) {
                    val label = measurer.measure(
                        count.toString(),
                        TextStyle(color = AahnikColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    )
                    drawText(
                        label,
                        topLeft = Offset(x + w / 2 - label.size.width / 2f, bottom - h - label.size.height - 2.dp.toPx()),
                        alpha = eased,
                    )
                }
            }
            if (i % labelEvery == 0) {
                val label = measurer.measure(TimeText.hourShort(hour, is24Hour), labelStyle)
                drawText(label, topLeft = Offset(x + w / 2 - label.size.width / 2f, size.height - label.size.height))
            }
        }

        selected?.let { hour ->
            val i = hours.indexOf(hour)
            val x = left + slot * i + slot / 2
            val count = hourCounts[hour]
            val text = "${TimeText.minute(hour * 60, is24Hour)} – ${TimeText.minute((hour + 1) * 60, is24Hour)}\n" +
                "$count completion${if (count == 1) "" else "s"}"
            drawTooltip(measurer, text, Offset(x, top + 8.dp.toPx()))
        }
    }
}
