package com.example.aahnik.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aahnik.ui.theme.AahnikColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Round check that fills with sloshing molten saffron when [done] becomes true,
 * then draws a tick on top. Draining is quick and calm.
 */
@Composable
fun LiquidCheck(done: Boolean, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val level = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) {
        level.animateTo(
            targetValue = if (done) 1f else 0f,
            animationSpec = if (done) spring(dampingRatio = 0.45f, stiffness = 90f) else tween(260),
        )
    }
    val tick by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = if (done) tween(380, delayMillis = 260) else tween(120),
        label = "tick",
    )
    val wave by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "wave-phase",
    )

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val stroke = 1.5.dp.toPx()
        val l = level.value

        drawCircle(Color.White.copy(alpha = 0.08f), radius = r)
        if (l > 0.001f) {
            val circle = Path().apply { addOval(Rect(center, r)) }
            clipPath(circle) {
                // Read the wave phase only while liquid is actually moving.
                val calm = l >= 0.999f
                val phase = if (calm) 0f else wave
                val amplitude = if (calm) 0f else this.size.height * 0.09f * (1f - l.coerceIn(0f, 1f)).coerceAtLeast(0.25f)
                val surface = this.size.height * (1f - l)
                val path = Path().apply {
                    moveTo(0f, surface)
                    val steps = 24
                    for (i in 0..steps) {
                        val x = this@Canvas.size.width * i / steps
                        val y = surface + sin(i / steps.toFloat() * 2f * PI.toFloat() * 1.2f + phase) * amplitude
                        lineTo(x, y)
                    }
                    lineTo(this@Canvas.size.width, this@Canvas.size.height)
                    lineTo(0f, this@Canvas.size.height)
                    close()
                }
                drawPath(
                    path,
                    Brush.verticalGradient(listOf(AahnikColors.Gold, AahnikColors.Saffron, AahnikColors.SaffronDeep)),
                )
                // Specular highlight on the liquid.
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(r * 0.65f, r * 0.55f),
                        radius = r * 0.7f,
                    ),
                    radius = r,
                )
            }
        }
        drawCircle(
            Color.White.copy(alpha = 0.35f + 0.35f * l.coerceIn(0f, 1f)),
            radius = r - stroke / 2,
            style = Stroke(stroke),
        )

        if (tick > 0f) {
            val check = Path().apply {
                moveTo(r * 0.55f, r * 1.02f)
                lineTo(r * 0.88f, r * 1.34f)
                lineTo(r * 1.48f, r * 0.70f)
            }
            val measure = PathMeasure().apply { setPath(check, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * tick, partial, true)
            drawPath(
                partial,
                color = Color(0xFF2B1600),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Glass orb showing today's progress as a glowing arc that springs to its new value. */
@Composable
fun ProgressOrb(done: Int, total: Int, modifier: Modifier = Modifier, size: Dp = 76.dp) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val sweep by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 120f),
        label = "orb-sweep",
    )
    val spin by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orb-spin",
    )
    Box(modifier.size(size).glass(CircleShape, fill = 0.10f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2 + 5.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke),
            )
            if (sweep > 0.001f) {
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(AahnikColors.SaffronDeep, AahnikColors.Gold, AahnikColors.Saffron, AahnikColors.SaffronDeep)
                        ),
                        startAngle = 0f, sweepAngle = 360f * sweep.coerceIn(0f, 1.05f), useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            if (total > 0 && done == total) {
                // Completed: a soft light orbits the ring.
                rotate(spin) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(center.x, inset),
                            radius = stroke * 2.2f,
                        ),
                        radius = stroke * 2.2f,
                        center = Offset(center.x, inset),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$done/$total",
                color = AahnikColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = "done",
                color = AahnikColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
            )
        }
    }
}
