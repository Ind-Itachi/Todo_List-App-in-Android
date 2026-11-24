package com.example.aahnik.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aahnik.data.Aahnik
import com.example.aahnik.ui.analysis.AnalysisScreen
import com.example.aahnik.ui.glass.GlassCard
import com.example.aahnik.ui.glass.LiquidBackground
import com.example.aahnik.ui.glass.LiquidSegmented
import com.example.aahnik.ui.glass.glass
import com.example.aahnik.ui.home.HomeScreen
import com.example.aahnik.ui.theme.AahnikColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import java.time.LocalTime

private sealed interface DialogState {
    data object Add : DialogState
    data class Edit(val aahnik: Aahnik) : DialogState
    data class Actions(val aahnik: Aahnik) : DialogState
    data class PickTime(val aahnik: Aahnik) : DialogState
}

private val barGlass = HazeStyle(
    backgroundColor = AahnikColors.NightTop,
    tint = HazeTint(Color.White.copy(alpha = 0.08f)),
    blurRadius = 28.dp,
    noiseFactor = 0.08f,
)

@Composable
fun AahnikApp(vm: AahnikViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val haptics = LocalHapticFeedback.current
    val hazeState = remember { HazeState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    Box(Modifier.fillMaxSize()) {
        // Everything inside this box is what the frosted tab bar blurs.
        Box(
            Modifier
                .fillMaxSize()
                .haze(hazeState)
        ) {
            LiquidBackground()
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 300f)) { it / 3 * dir } +
                        fadeIn(tween(260)) +
                        scaleIn(spring(dampingRatio = 0.8f, stiffness = 300f), initialScale = 0.96f)) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(180)))
                },
                label = "tabs",
            ) { current ->
                if (current == 0) {
                    HomeScreen(
                        state = state,
                        is24Hour = is24Hour,
                        onTap = { a ->
                            if (state.todayDone.containsKey(a.id)) {
                                dialog = DialogState.Actions(a)
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.markDoneNow(a)
                            }
                        },
                        onLongPress = { a -> dialog = DialogState.Actions(a) },
                    )
                } else {
                    AnalysisScreen(state = state, is24Hour = is24Hour)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidSegmented(
                count = 2,
                selected = tab,
                onSelect = { tab = it },
                height = 64.dp,
                inset = 6.dp,
                glassBackground = false,
                modifier = Modifier
                    .width(236.dp)
                    .clip(CircleShape)
                    .hazeChild(hazeState, barGlass)
                    .glass(CircleShape, fill = 0.03f),
            ) { i, selected ->
                val color = if (selected) AahnikColors.TextPrimary else AahnikColors.TextMuted
                if (i == 0) {
                    Icon(Icons.Rounded.Home, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                } else {
                    ChartGlyph(color, Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (i == 0) "Today" else "Analysis",
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }

            AnimatedVisibility(
                visible = tab == 0,
                enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 400f)) + fadeIn(),
                exit = scaleOut(tween(160)) + fadeOut(tween(160)),
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    GlassCard(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .hazeChild(hazeState, barGlass),
                        shape = CircleShape,
                        fill = 0.08f,
                        rim = AahnikColors.Gold,
                        onClick = { dialog = DialogState.Add },
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Add aahnik",
                            tint = AahnikColors.Gold,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(30.dp),
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        DialogState.Add -> AahnikEditorDialog(
            existing = null,
            onDismiss = { dialog = null },
            onSave = { name, icon -> vm.addAahnik(name, icon); dialog = null },
        )
        is DialogState.Edit -> AahnikEditorDialog(
            existing = d.aahnik,
            onDismiss = { dialog = null },
            onSave = { name, icon -> vm.updateAahnik(d.aahnik, name, icon); dialog = null },
            onDelete = { vm.deleteAahnik(d.aahnik); dialog = null },
        )
        is DialogState.Actions -> AahnikActionsDialog(
            aahnik = d.aahnik,
            completion = state.todayDone[d.aahnik.id],
            is24Hour = is24Hour,
            onDismiss = { dialog = null },
            onPickTime = { dialog = DialogState.PickTime(d.aahnik) },
            onUndo = { vm.undoToday(d.aahnik); dialog = null },
            onEdit = { dialog = DialogState.Edit(d.aahnik) },
        )
        is DialogState.PickTime -> {
            val existing = state.todayDone[d.aahnik.id]
            CompletionTimeDialog(
                aahnik = d.aahnik,
                initial = existing?.let { LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60) } ?: LocalTime.now(),
                is24Hour = is24Hour,
                onDismiss = { dialog = null },
                onConfirm = { time -> vm.markDoneAt(d.aahnik, time); dialog = null },
            )
        }
    }
}

/** Small bar-chart glyph for the Analysis tab. */
@Composable
private fun ChartGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width / 5f
        val heights = listOf(0.45f, 0.8f, 0.6f)
        heights.forEachIndexed { i, h ->
            val barH = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(w * (i * 2), size.height - barH),
                size = Size(w, barH),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}
