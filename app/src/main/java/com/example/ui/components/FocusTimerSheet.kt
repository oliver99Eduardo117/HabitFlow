package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.DateUtils
import com.example.viewmodel.ActiveTimerState

private const val MAX_MINUTES_24_HOURS = 1440

@Composable
fun FocusTimerSheet(
    timerState: ActiveTimerState,
    onTogglePlayPause: () -> Unit,
    onResetTimer: () -> Unit,
    onCompleteEarly: () -> Unit,
    onConfigure: (isPomodoro: Boolean, durationMinutes: Int) -> Unit,
    onStartDiscardingForeign: (isPomodoro: Boolean, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Sesión de un hábito corriendo o en pausa: Enfoque no la muestra, la trata como ajena
    val isForeignSession = timerState.habitId != null && (timerState.isRunning || timerState.isPaused)
    var foreignPomodoro by remember { mutableStateOf(true) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isPomodoroMode = if (isForeignSession) foreignPomodoro else timerState.isPomodoro

    val standardPresets = listOf(5, 25, 45, 60)
    // Duración configurada en el TimerManager (null en modo libre, donde totalSeconds es 0)
    val configuredMinutes = if (!isForeignSession && timerState.isPomodoro && timerState.totalSeconds > 0) {
        timerState.totalSeconds / 60
    } else {
        null
    }
    val initialMinutes = configuredMinutes ?: 25
    var selectedPresetMinutes by remember { mutableIntStateOf(initialMinutes) }
    var isCustomMode by remember { mutableStateOf(initialMinutes !in standardPresets) }
    var customMinutesInput by remember { mutableStateOf(initialMinutes.toString()) }

    val parsedCustomMinutes = customMinutesInput.toIntOrNull() ?: 0
    val isCustomInputInvalid = isCustomMode && (parsedCustomMinutes < 1 || parsedCustomMinutes > MAX_MINUTES_24_HOURS)

    val effectiveMinutes = if (isCustomMode) {
        parsedCustomMinutes.coerceIn(1, MAX_MINUTES_24_HOURS)
    } else {
        selectedPresetMinutes
    }

    // Si la duración cambia desde fuera de esta pantalla, refleja el nuevo valor en los chips
    LaunchedEffect(configuredMinutes) {
        val mins = configuredMinutes ?: return@LaunchedEffect
        if (mins != effectiveMinutes) {
            isCustomMode = mins !in standardPresets
            selectedPresetMinutes = mins
            customMinutesInput = mins.toString()
        }
    }

    // Estado que se dibuja: con sesión ajena, una vista en reposo con la configuración local
    val view = if (isForeignSession) {
        val secs = if (foreignPomodoro) effectiveMinutes * 60 else 0
        ActiveTimerState(isPomodoro = foreignPomodoro, totalSeconds = secs, remainingSeconds = secs)
    } else {
        timerState
    }
    val hasActiveSession = view.isRunning || view.isPaused
    val applyConfig: (Boolean, Int) -> Unit = { pomo, mins ->
        if (isForeignSession) foreignPomodoro = pomo else onConfigure(pomo, mins)
    }

    val displaySeconds = if (view.isPomodoro) view.remainingSeconds else view.elapsedSeconds
    val formattedTime = DateUtils.formatTimerDisplay(displaySeconds)

    val progress = if (view.isPomodoro && view.totalSeconds > 0) {
        (view.totalSeconds - view.remainingSeconds).toFloat() / view.totalSeconds
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
                text = "Modo Enfoque Profundo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (view.isRunning) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (view.isRunning) "Sesión en curso • Gana +2 XP/min" else "Selecciona tu modo y prepárate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isForeignSession) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hay una sesión de hábito en segundo plano",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Selector: Pomodoro vs Libre (Cronómetro)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isPomodoroMode,
                    enabled = !hasActiveSession,
                    onClick = {
                        if (!isPomodoroMode) applyConfig(true, effectiveMinutes)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Pomodoro (Cuenta atrás)")
                }
                SegmentedButton(
                    selected = !isPomodoroMode,
                    enabled = !hasActiveSession,
                    onClick = {
                        if (isPomodoroMode) applyConfig(false, effectiveMinutes)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Cronómetro Libre")
                }
            }

            if (isPomodoroMode && !hasActiveSession) {
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
                                applyConfig(true, mins)
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
                                        applyConfig(true, p)
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
                                applyConfig(true, next)
                            },
                            label = { Text("+15m") }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        SuggestionChip(
                            onClick = {
                                val next = (parsedCustomMinutes + 60).coerceIn(1, MAX_MINUTES_24_HOURS)
                                customMinutesInput = next.toString()
                                selectedPresetMinutes = next
                                applyConfig(true, next)
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
                color = if (view.isRunning) Color(0xFF6366F1) else MaterialTheme.colorScheme.primary,
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

        // Control Buttons: Reset, Play/Pause, Finish & Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            IconButton(
                onClick = onResetTimer,
                enabled = !isForeignSession,
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
                onClick = {
                    if (isForeignSession) showDiscardDialog = true else onTogglePlayPause()
                },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (view.isRunning) Color(0xFFF43F5E) else Color(0xFF6366F1)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (view.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (view.isRunning) "Pausar" else "Iniciar",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Save Progress / Finish Early
            IconButton(
                onClick = onCompleteEarly,
                enabled = view.elapsedSeconds > 0 || (view.isPomodoro && view.remainingSeconds < view.totalSeconds),
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

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("¿Iniciar sesión libre?") },
                text = { Text("La sesión de hábito en curso se descartará sin guardar su tiempo.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        onStartDiscardingForeign(isPomodoroMode, effectiveMinutes)
                    }) {
                        Text("Descartar e iniciar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}
