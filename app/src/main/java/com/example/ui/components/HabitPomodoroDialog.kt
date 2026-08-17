package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.HabitWithStats
import com.example.util.DateUtils
import com.example.util.IconHelper
import com.example.viewmodel.ActiveTimerState

private const val MAX_MINUTES_24_HOURS = 1440 // 24 hours = 1440 minutes

@Composable
fun HabitPomodoroDialog(
    habitWithStats: HabitWithStats,
    timerState: ActiveTimerState,
    onDismiss: () -> Unit,
    onStartTimer: (durationMinutes: Int, isPomodoro: Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onResetTimer: () -> Unit,
    onCompleteAndSave: () -> Unit,
    onOpenFullScreen: () -> Unit
) {
    val habit = habitWithStats.habit
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    // Check if this habit is the one currently running in the timer
    val isCurrentHabitActive = timerState.habitId == habit.id
    var isPomodoroMode by remember {
        mutableStateOf(if (isCurrentHabitActive) timerState.isPomodoro else true)
    }

    val standardPresets = listOf(5, 25, 45, 60)
    val initialMinutes = if (isCurrentHabitActive && timerState.isPomodoro && timerState.totalSeconds > 0) {
        timerState.totalSeconds / 60
    } else if (habit.timerDurationMinutes in standardPresets) {
        habit.timerDurationMinutes
    } else if (habit.timerDurationMinutes in 1..MAX_MINUTES_24_HOURS) {
        habit.timerDurationMinutes
    } else {
        25
    }

    var selectedPresetMinutes by remember { mutableIntStateOf(initialMinutes) }
    var isCustomMinutesMode by remember {
        mutableStateOf(initialMinutes !in standardPresets)
    }
    var customMinutesInput by remember {
        mutableStateOf(selectedPresetMinutes.toString())
    }

    val parsedCustomMinutes = customMinutesInput.toIntOrNull() ?: 0
    val isCustomInputInvalid = isCustomMinutesMode && (parsedCustomMinutes < 1 || parsedCustomMinutes > MAX_MINUTES_24_HOURS)

    val effectiveMinutes = if (isCustomMinutesMode) {
        parsedCustomMinutes.coerceIn(1, MAX_MINUTES_24_HOURS)
    } else {
        selectedPresetMinutes
    }

    val displaySeconds = if (isCurrentHabitActive) {
        if (timerState.isPomodoro) timerState.remainingSeconds else timerState.elapsedSeconds
    } else {
        if (isPomodoroMode) effectiveMinutes * 60 else 0
    }

    val formattedTime = DateUtils.formatTimerDisplay(displaySeconds)

    val progress = if (isCurrentHabitActive && timerState.isPomodoro && timerState.totalSeconds > 0) {
        (timerState.totalSeconds - timerState.remainingSeconds).toFloat() / timerState.totalSeconds
    } else {
        0f
    }

    val isRunning = isCurrentHabitActive && timerState.isRunning
    val canStart = !isCustomInputInvalid && effectiveMinutes in 1..MAX_MINUTES_24_HOURS

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("habit_pomodoro_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Habit info & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(habitColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconHelper.getIconByName(habit.iconName),
                                contentDescription = null,
                                tint = habitColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = habit.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${habit.category} • Cronómetro Pomodoro",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector: Pomodoro (Cuenta atrás) vs Cronómetro Libre
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isPomodoroMode,
                        onClick = {
                            if (!isRunning) {
                                isPomodoroMode = true
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = !isRunning
                    ) {
                        Text("Pomodoro", style = MaterialTheme.typography.labelMedium)
                    }
                    SegmentedButton(
                        selected = !isPomodoroMode,
                        onClick = {
                            if (!isRunning) {
                                isPomodoroMode = false
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = !isRunning
                    ) {
                        Text("Libre", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Presets & Custom Duration (5, 25, 45, 60 min, X min)
                if (isPomodoroMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        standardPresets.forEach { mins ->
                            val isSelected = !isCustomMinutesMode && selectedPresetMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (!isRunning) {
                                        isCustomMinutesMode = false
                                        selectedPresetMinutes = mins
                                        customMinutesInput = mins.toString()
                                    }
                                },
                                label = {
                                    Text(
                                        text = "${mins}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isRunning
                            )
                        }

                        // Custom "X min" chip
                        FilterChip(
                            selected = isCustomMinutesMode,
                            onClick = {
                                if (!isRunning) {
                                    isCustomMinutesMode = true
                                    if (customMinutesInput.isEmpty() || parsedCustomMinutes <= 0) {
                                        customMinutesInput = selectedPresetMinutes.toString()
                                    }
                                }
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = if (isCustomMinutesMode && parsedCustomMinutes in 1..MAX_MINUTES_24_HOURS) {
                                            "${parsedCustomMinutes}m"
                                        } else {
                                            "X min"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isCustomMinutesMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.3f),
                            enabled = !isRunning
                        )
                    }

                    // Expandable Custom Minutes Editor
                    AnimatedVisibility(visible = isCustomMinutesMode && !isRunning) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Duración personalizada",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Máx: 24h (1440 min)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = customMinutesInput,
                                        onValueChange = { input ->
                                            val digitsOnly = input.filter { it.isDigit() }
                                            if (digitsOnly.length <= 5) {
                                                customMinutesInput = digitsOnly
                                                val parsed = digitsOnly.toIntOrNull()
                                                if (parsed != null && parsed in 1..MAX_MINUTES_24_HOURS) {
                                                    selectedPresetMinutes = parsed
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        label = { Text("Minutos (1 - 1440)") },
                                        trailingIcon = {
                                            Text(
                                                "min",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 12.dp)
                                            )
                                        },
                                        isError = isCustomInputInvalid,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Quick adjustment buttons
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            SuggestionChip(
                                                onClick = {
                                                    val next = (parsedCustomMinutes - 15).coerceIn(1, MAX_MINUTES_24_HOURS)
                                                    customMinutesInput = next.toString()
                                                    selectedPresetMinutes = next
                                                },
                                                label = { Text("-15m", style = MaterialTheme.typography.labelSmall) }
                                            )
                                            SuggestionChip(
                                                onClick = {
                                                    val next = (parsedCustomMinutes + 15).coerceIn(1, MAX_MINUTES_24_HOURS)
                                                    customMinutesInput = next.toString()
                                                    selectedPresetMinutes = next
                                                },
                                                label = { Text("+15m", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            SuggestionChip(
                                                onClick = {
                                                    val next = (parsedCustomMinutes + 60).coerceIn(1, MAX_MINUTES_24_HOURS)
                                                    customMinutesInput = next.toString()
                                                    selectedPresetMinutes = next
                                                },
                                                label = { Text("+1h", style = MaterialTheme.typography.labelSmall) }
                                            )
                                            SuggestionChip(
                                                onClick = {
                                                    val next = (parsedCustomMinutes + 120).coerceIn(1, MAX_MINUTES_24_HOURS)
                                                    customMinutesInput = next.toString()
                                                    selectedPresetMinutes = next
                                                },
                                                label = { Text("+2h", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }

                                if (isCustomInputInvalid) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (parsedCustomMinutes > MAX_MINUTES_24_HOURS) {
                                            "⚠️ El tiempo no puede superar las 24 horas (1440 min)"
                                        } else {
                                            "⚠️ Ingresa un valor válido de 1 a 1440 minutos"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (parsedCustomMinutes > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "⏱️ Tiempo estimado: ${DateUtils.formatMinutesReadable(parsedCustomMinutes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cronómetro libre para registrar tu tiempo sin límite",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Circular Timer Gauge
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { if (isPomodoroMode) progress else 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = if (isRunning) habitColor else MaterialTheme.colorScheme.primary,
                        strokeWidth = 9.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        strokeCap = StrokeCap.Round
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formattedTime,
                            fontSize = if (displaySeconds >= 3600) 28.sp else 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRunning) "🔥 En progreso" else if (isPomodoroMode) "Tiempo restante" else "Tiempo transcurrido",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRunning) habitColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Control Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset button
                    IconButton(
                        onClick = {
                            if (isCurrentHabitActive) {
                                onResetTimer()
                            }
                        },
                        enabled = isCurrentHabitActive,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reiniciar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Play / Pause Main Button
                    Button(
                        onClick = {
                            if (!isCurrentHabitActive) {
                                onStartTimer(effectiveMinutes, isPomodoroMode)
                            } else {
                                onTogglePlayPause()
                            }
                        },
                        enabled = isCurrentHabitActive || canStart,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .testTag("pomodoro_dialog_play_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFF43F5E) else habitColor
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Pausar" else "Iniciar",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Save / Finish Button
                    val canSave = isCurrentHabitActive && (
                        timerState.elapsedSeconds > 0 ||
                        (timerState.isPomodoro && timerState.remainingSeconds < timerState.totalSeconds)
                    )
                    IconButton(
                        onClick = {
                            onCompleteAndSave()
                            onDismiss()
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (canSave) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Guardar y registrar tiempo",
                            tint = if (canSave) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Background & Lockscreen persistence indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isRunning) habitColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRunning) "Activo en segundo plano y pantalla bloqueada" else "Funciona con pantalla apagada y bloqueada",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option to expand full screen
                TextButton(
                    onClick = {
                        if (!isCurrentHabitActive && canStart) {
                            onStartTimer(effectiveMinutes, isPomodoroMode)
                        }
                        onOpenFullScreen()
                        onDismiss()
                    },
                    enabled = isCurrentHabitActive || canStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Abrir en pantalla completa", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
