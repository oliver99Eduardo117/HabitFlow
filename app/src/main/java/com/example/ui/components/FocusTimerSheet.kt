package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.util.DateUtils
import com.example.viewmodel.ActiveTimerState

private const val MAX_MINUTES_24_HOURS = 1440

@Composable
fun FocusTimerSheet(
    timerState: ActiveTimerState,
    habits: List<Habit>,
    onTogglePlayPause: () -> Unit,
    onResetTimer: () -> Unit,
    onCompleteEarly: () -> Unit,
    onSelectHabitForTimer: (Habit, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPomodoroMode by remember { mutableStateOf(timerState.isPomodoro) }
    val standardPresets = listOf(5, 25, 45, 60)
    var selectedPresetMinutes by remember { mutableIntStateOf(25) }
    var isCustomMode by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("25") }

    val parsedCustomMinutes = customMinutesInput.toIntOrNull() ?: 0
    val isCustomInputInvalid = isCustomMode && (parsedCustomMinutes < 1 || parsedCustomMinutes > MAX_MINUTES_24_HOURS)

    val effectiveMinutes = if (isCustomMode) {
        parsedCustomMinutes.coerceIn(1, MAX_MINUTES_24_HOURS)
    } else {
        selectedPresetMinutes
    }

    val displaySeconds = if (timerState.isPomodoro) timerState.remainingSeconds else timerState.elapsedSeconds
    val formattedTime = DateUtils.formatTimerDisplay(displaySeconds)

    val progress = if (timerState.isPomodoro && timerState.totalSeconds > 0) {
        (timerState.totalSeconds - timerState.remainingSeconds).toFloat() / timerState.totalSeconds
    } else {
        1f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (timerState.habitTitle.isNotEmpty()) timerState.habitTitle else "Modo Enfoque Profundo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (timerState.isRunning) "🔥 Sesión en curso • Gana +2 XP/min" else "Selecciona tu modo y prepárate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Selector: Pomodoro vs Libre (Cronómetro)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isPomodoroMode,
                    onClick = {
                        isPomodoroMode = true
                        val h = habits.find { it.id == timerState.habitId } ?: habits.firstOrNull()
                        if (h != null) onSelectHabitForTimer(h.copy(timerDurationMinutes = effectiveMinutes), true)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Pomodoro (Cuenta atrás)")
                }
                SegmentedButton(
                    selected = !isPomodoroMode,
                    onClick = {
                        isPomodoroMode = false
                        val h = habits.find { it.id == timerState.habitId } ?: habits.firstOrNull()
                        if (h != null) onSelectHabitForTimer(h, false)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Cronómetro Libre")
                }
            }

            if (isPomodoroMode && !timerState.isRunning) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    standardPresets.forEach { mins ->
                        val isSelected = !isCustomMode && selectedPresetMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                isCustomMode = false
                                selectedPresetMinutes = mins
                                customMinutesInput = mins.toString()
                                val h = habits.find { it.id == timerState.habitId } ?: habits.firstOrNull()
                                if (h != null) onSelectHabitForTimer(h.copy(timerDurationMinutes = mins), true)
                            },
                            label = { Text("${mins}m") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FilterChip(
                        selected = isCustomMode,
                        onClick = {
                            isCustomMode = true
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
                                    text = if (isCustomMode && parsedCustomMinutes in 1..MAX_MINUTES_24_HOURS) {
                                        "${parsedCustomMinutes}m"
                                    } else {
                                        "X min"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.weight(1.3f)
                    )
                }

                AnimatedVisibility(visible = isCustomMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customMinutesInput,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }
                                if (digits.length <= 5) {
                                    customMinutesInput = digits
                                    val p = digits.toIntOrNull()
                                    if (p != null && p in 1..MAX_MINUTES_24_HOURS) {
                                        selectedPresetMinutes = p
                                        val h = habits.find { it.id == timerState.habitId } ?: habits.firstOrNull()
                                        if (h != null) onSelectHabitForTimer(h.copy(timerDurationMinutes = p), true)
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Minutos (1 - 1440 / 24h)") },
                            isError = isCustomInputInvalid,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {
                                val next = (parsedCustomMinutes + 15).coerceIn(1, MAX_MINUTES_24_HOURS)
                                customMinutesInput = next.toString()
                                selectedPresetMinutes = next
                                val h = habits.find { it.id == timerState.habitId } ?: habits.firstOrNull()
                                if (h != null) onSelectHabitForTimer(h.copy(timerDurationMinutes = next), true)
                            },
                            label = { Text("+15m") }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        SuggestionChip(
                            onClick = {
                                val next = (parsedCustomMinutes + 60).coerceIn(1, MAX_MINUTES_24_HOURS)
                                customMinutesInput = next.toString()
                                selectedPresetMinutes = next
                                val h = habits.find { it.id == timerState.habitId } ?: habits.firstOrNull()
                                if (h != null) onSelectHabitForTimer(h.copy(timerDurationMinutes = next), true)
                            },
                            label = { Text("+1h") }
                        )
                    }
                }
            }
        }

        // Circular Timer Display
        Box(
            modifier = Modifier
                .size(250.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Circular Progress Indicator
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = if (timerState.isRunning) Color(0xFF6366F1) else MaterialTheme.colorScheme.primary,
                strokeWidth = 11.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                strokeCap = StrokeCap.Round
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    fontSize = if (displaySeconds >= 3600) 36.sp else 46.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPomodoroMode) "Tiempo Restante" else "Tiempo Transcurrido",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Habit Linked Dropdown/Chip
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (timerState.habitTitle.isNotEmpty()) "Asociado: ${timerState.habitTitle}" else "Sin hábito asignado",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Bloqueo OK",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Control Buttons: Reset, Play/Pause, Finish & Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            IconButton(
                onClick = onResetTimer,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reiniciar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Play / Pause Main Button
            Button(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (timerState.isRunning) Color(0xFFF43F5E) else Color(0xFF6366F1)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (timerState.isRunning) "Pausar" else "Iniciar",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Save Progress / Finish Early
            IconButton(
                onClick = onCompleteEarly,
                enabled = timerState.elapsedSeconds > 0 || (timerState.isPomodoro && timerState.remainingSeconds < timerState.totalSeconds),
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Guardar y finalizar",
                    tint = Color(0xFF10B981)
                )
            }
        }
    }
}
