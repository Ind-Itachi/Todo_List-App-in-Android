package com.example.aahnik.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aahnik.data.Aahnik
import com.example.aahnik.data.Completion
import com.example.aahnik.data.TimeText
import com.example.aahnik.ui.glass.GlassButton
import com.example.aahnik.ui.glass.GlassCard
import com.example.aahnik.ui.glass.glass
import com.example.aahnik.ui.theme.AahnikColors
import java.time.LocalTime

val AahnikIcons = listOf("🪔", "🙏", "📿", "📖", "🛕", "🌅", "🌙", "🕉️", "🌸", "🔔", "🎵", "🧘", "💧", "🍃", "⭐", "🏠")

/** Dialog whose card pops in like a droplet and is made of dark frosted glass. */
@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f)) }
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.86f + 0.14f * pop.value
                    scaleX = s
                    scaleY = s
                    alpha = pop.value.coerceIn(0f, 1f)
                }
                .background(
                    Brush.verticalGradient(listOf(Color(0xE6272150), Color(0xF0151230))),
                    RoundedCornerShape(32.dp),
                )
                .glass(RoundedCornerShape(32.dp), fill = 0.06f)
        ) {
            Column(Modifier.padding(22.dp), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AahnikEditorDialog(
    existing: Aahnik?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: AahnikIcons.first()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) focus.requestFocus() }

    GlassDialog(onDismiss) {
        Text(
            if (existing == null) "New aahnik" else "Edit aahnik",
            style = MaterialTheme.typography.headlineSmall,
            color = AahnikColors.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(18.dp), fill = 0.08f)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (name.isEmpty()) {
                Text("e.g. Nitya puja", color = AahnikColors.TextMuted, fontSize = 17.sp)
            }
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                textStyle = TextStyle(color = AahnikColors.TextPrimary, fontSize = 17.sp),
                cursorBrush = SolidColor(AahnikColors.Gold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Icon", color = AahnikColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AahnikIcons.forEach { option ->
                val selected = option == icon
                GlassCard(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    fill = if (selected) 0.28f else 0.06f,
                    rim = if (selected) AahnikColors.Gold else Color.White,
                    onClick = { icon = option },
                ) {
                    Text(option, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDelete != null) {
                GlassButton(
                    text = if (confirmDelete) "Confirm" else "Delete",
                    destructive = true,
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                )
            }
            Spacer(Modifier.weight(1f))
            GlassButton("Cancel", onClick = onDismiss)
            GlassButton(
                "Save",
                primary = true,
                enabled = name.isNotBlank(),
                onClick = { onSave(name, icon) },
            )
        }
        if (confirmDelete) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Deleting also removes all of this aahnik's completion history.",
                color = Color(0xFFFFB4AB),
                fontSize = 12.sp,
            )
        }
    }
}

/** Options for a single aahnik: change today's time, undo, edit. */
@Composable
fun AahnikActionsDialog(
    aahnik: Aahnik,
    completion: Completion?,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onPickTime: () -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
) {
    GlassDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(aahnik.icon, fontSize = 30.sp)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(aahnik.name, style = MaterialTheme.typography.headlineSmall, color = AahnikColors.TextPrimary)
                Text(
                    completion?.let { "Completed today at ${TimeText.minute(it.minuteOfDay, is24Hour)}" } ?: "Not completed yet today",
                    color = if (completion != null) AahnikColors.Gold else AahnikColors.TextMuted,
                    fontSize = 14.sp,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                if (completion != null) "Change completion time" else "Mark done at a different time",
                onClick = onPickTime,
                modifier = Modifier.fillMaxWidth(),
            )
            if (completion != null) {
                GlassButton("Undo today's completion", onClick = onUndo, modifier = Modifier.fillMaxWidth())
            }
            GlassButton("Edit or delete", onClick = onEdit, modifier = Modifier.fillMaxWidth())
            GlassButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionTimeDialog(
    aahnik: Aahnik,
    initial: LocalTime,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    val now = LocalTime.now()
    val picked = LocalTime.of(state.hour, state.minute)
    val inFuture = picked.isAfter(now)

    GlassDialog(onDismiss) {
        Text("${aahnik.icon} ${aahnik.name}", style = MaterialTheme.typography.headlineSmall, color = AahnikColors.TextPrimary)
        Text("When did you complete it today?", color = AahnikColors.TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        TimePicker(
            state = state,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = TimePickerDefaults.colors(
                clockDialColor = Color.White.copy(alpha = 0.08f),
                clockDialSelectedContentColor = Color(0xFF2B1600),
                clockDialUnselectedContentColor = AahnikColors.TextPrimary,
                selectorColor = AahnikColors.Saffron,
                containerColor = Color.Transparent,
                periodSelectorBorderColor = Color.White.copy(alpha = 0.3f),
                periodSelectorSelectedContainerColor = AahnikColors.Saffron.copy(alpha = 0.35f),
                periodSelectorUnselectedContainerColor = Color.Transparent,
                periodSelectorSelectedContentColor = AahnikColors.TextPrimary,
                periodSelectorUnselectedContentColor = AahnikColors.TextMuted,
                timeSelectorSelectedContainerColor = AahnikColors.Saffron.copy(alpha = 0.35f),
                timeSelectorUnselectedContainerColor = Color.White.copy(alpha = 0.08f),
                timeSelectorSelectedContentColor = AahnikColors.TextPrimary,
                timeSelectorUnselectedContentColor = AahnikColors.TextSecondary,
            ),
        )
        if (inFuture) {
            Text(
                "That time hasn't happened yet today.",
                color = Color(0xFFFFB4AB),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            GlassButton("Cancel", onClick = onDismiss)
            GlassButton("Save", primary = true, enabled = !inFuture, onClick = { onConfirm(picked) })
        }
    }
}
