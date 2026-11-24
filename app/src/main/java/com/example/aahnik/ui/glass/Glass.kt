package com.example.aahnik.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aahnik.ui.theme.AahnikColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class Blob(
    val color: Color,
    val x: Float, val y: Float,
    val ax: Float, val ay: Float,
    val fx: Int, val fy: Int,
    val phase: Float,
    val radius: Float,
    val alpha: Float,
)

private val blobs = listOf(
    Blob(Color(0xFFFF9F43), 0.85f, 0.10f, 0.12f, 0.06f, 1, 2, 0.0f, 0.55f, 0.55f), // saffron
    Blob(Color(0xFFE84393), 0.10f, 0.35f, 0.10f, 0.10f, 2, 1, 1.3f, 0.60f, 0.40f), // rose
    Blob(Color(0xFF6C5CE7), 0.70f, 0.60f, 0.15f, 0.10f, 1, 1, 2.4f, 0.75f, 0.55f), // violet
    Blob(Color(0xFF00CEC9), 0.20f, 0.90f, 0.12f, 0.06f, 1, 2, 3.7f, 0.60f, 0.35f), // teal
    Blob(Color(0xFF0984E3), 0.15f, 0.05f, 0.08f, 0.05f, 2, 1, 4.6f, 0.50f, 0.45f), // blue
)

/** Slowly drifting aurora of colour blobs that the glass surfaces sit on. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 48_000, easing = LinearEasing)),
        label = "aurora-phase",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(AahnikColors.NightTop, AahnikColors.NightBottom)))
        for (b in blobs) {
            val center = Offset(
                x = size.width * (b.x + b.ax * sin(t * b.fx + b.phase)),
                y = size.height * (b.y + b.ay * cos(t * b.fy + b.phase)),
            )
            val r = size.maxDimension * b.radius
            drawCircle(
                brush = Brush.radialGradient(
                    0f to b.color.copy(alpha = b.alpha),
                    0.45f to b.color.copy(alpha = b.alpha * 0.45f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
    }
}

/**
 * Glass material: translucent fill that is brighter at the top, a soft top sheen,
 * and a specular rim that catches light on the top-left and bottom-right edges.
 */
fun Modifier.glass(
    shape: Shape,
    fill: Float = 0.10f,
    rim: Color = Color.White,
    rimAlpha: Float = 1f,
    glow: Color = Color.Transparent,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = fill + 0.07f), Color.White.copy(alpha = fill * 0.45f))
        )
    )
    .drawBehind {
        if (glow.alpha > 0f) {
            drawRect(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = glow.alpha * 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.5f),
                    radius = size.maxDimension * 0.75f,
                )
            )
        }
        drawRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                endY = size.height * 0.5f,
            )
        )
    }
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                rim.copy(alpha = 0.60f * rimAlpha),
                rim.copy(alpha = 0.10f * rimAlpha),
                rim.copy(alpha = 0.04f * rimAlpha),
                rim.copy(alpha = 0.32f * rimAlpha),
            )
        ),
        shape = shape,
    )

/**
 * A pressable glass surface. Pressing squishes it like a drop of liquid (bouncy spring)
 * and a light bloom follows the finger.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    fill: Float = 0.10f,
    rim: Color = Color.White,
    glow: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)

    val squish by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) spring(dampingRatio = 0.8f, stiffness = 900f)
        else spring(dampingRatio = 0.32f, stiffness = 320f),
        label = "squish",
    )
    val bloom by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 120 else 520),
        label = "bloom",
    )

    val interactive = onClick != null || onLongClick != null
    Box(
        modifier = modifier
            .graphicsLayer {
                // Liquid squish: compresses more vertically than horizontally, then wobbles back.
                scaleX = 1f - 0.025f * squish
                scaleY = 1f - 0.045f * squish
            }
            .glass(shape = shape, fill = fill, rim = rim, glow = glow)
            .drawBehind {
                if (bloom > 0f) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.22f * bloom), Color.Transparent),
                            center = touch,
                            radius = size.maxDimension * 0.55f,
                        ),
                        radius = size.maxDimension * 0.55f,
                        center = touch,
                    )
                }
            }
            .then(
                if (!interactive) Modifier else Modifier
                    .semantics {
                        role = Role.Button
                        if (onClick != null) semanticsOnClick { click?.invoke(); true }
                        if (onLongClick != null) semanticsOnLongClick { longClick?.invoke(); true }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                touch = offset
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { click?.invoke() },
                            onLongPress = {
                                longClick?.let {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    it()
                                }
                            },
                        )
                    }
            ),
        content = content,
    )
}

/**
 * Segmented control whose selection bubble moves like a droplet: the leading edge springs
 * ahead and the trailing edge catches up, so the bubble stretches and settles.
 */
@Composable
fun LiquidSegmented(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    inset: Dp = 4.dp,
    glassBackground: Boolean = true,
    item: @Composable RowScope.(index: Int, isSelected: Boolean) -> Unit,
) {
    var lastSelected by remember { mutableIntStateOf(selected) }
    var movingRight by remember { mutableStateOf(true) }
    if (selected != lastSelected) {
        movingRight = selected > lastSelected
        lastSelected = selected
    }
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier
            .height(height)
            .then(if (glassBackground) Modifier.glass(CircleShape, fill = 0.06f) else Modifier)
    ) {
        val segment = maxWidth / count
        val fast = spring<Dp>(dampingRatio = 0.72f, stiffness = 520f)
        val slow = spring<Dp>(dampingRatio = 0.78f, stiffness = 170f)
        val left by animateDpAsState(segment * selected, if (movingRight) slow else fast, label = "left")
        val right by animateDpAsState(segment * (selected + 1), if (movingRight) fast else slow, label = "right")
        val stretch = ((right - left) / segment - 1f).coerceIn(0f, 1f)

        Box(
            Modifier
                .offset(x = left + inset)
                .width((right - left - inset * 2).coerceAtLeast(0.dp))
                .fillMaxHeight()
                .padding(vertical = inset)
                .graphicsLayer { scaleY = 1f - 0.18f * stretch }
                .glass(CircleShape, fill = 0.20f)
        )
        Row(Modifier.fillMaxSize()) {
            repeat(count) { i ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = i == selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (i != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            }
                        },
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { item(i, i == selected) }
            }
        }
    }
}

/** Pill button. [primary] buttons are filled with molten saffron; others are clear glass. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val enabledAlpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "enabled")
    GlassCard(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { alpha = enabledAlpha }
            .then(
                if (primary) Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AahnikColors.Gold.copy(alpha = 0.85f), AahnikColors.SaffronDeep.copy(alpha = 0.85f))
                        )
                    ) else Modifier
            ),
        shape = CircleShape,
        fill = if (primary) 0.05f else 0.10f,
        rim = if (destructive) Color(0xFFFF8A80) else Color.White,
        onClick = if (enabled) onClick else null,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp),
            color = when {
                primary -> Color(0xFF2B1600)
                destructive -> Color(0xFFFFB4AB)
                else -> AahnikColors.TextPrimary
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Physics spring presets shared across the app. */
object LiquidSprings {
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
}
