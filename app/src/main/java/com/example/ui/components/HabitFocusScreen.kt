package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.util.DateUtils
import com.example.util.IconHelper
import com.example.viewmodel.ActiveTimerState

private const val MAX_MINUTES_24_HOURS = 1440

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFocusScreen(
    habit: Habit,
    timerState: ActiveTimerState,
    initialMinutes: Int,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onResetTimer: () -> Unit,
    onCompleteEarly: () -> Unit,
    onChangeMode: (isPomodoro: Boolean) -> Unit,
    onConfigureMinutes: (minutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val habitColor = remember(habit.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(habit.colorHex)) }
            .getOrDefault(Color(0xFF6366F1))
    }

    val isPomodoroMode = timerState.isPomodoro
    val hasActiveSession = timerState.isRunning || timerState.isPaused

    val standardPresets = remember(initialMinutes) {
        (listOf(5, 15, 25, 45) + initialMinutes).distinct().sorted()
    }

    val configuredMinutes = if (timerState.isPomodoro && timerState.totalSeconds > 0) {
        timerState.totalSeconds / 60
    } else {
        null
    }
    val effectiveInitMinutes = configuredMinutes ?: initialMinutes

    var selectedPresetMinutes by remember(effectiveInitMinutes) { mutableIntStateOf(effectiveInitMinutes) }
    var isCustomMode by remember(effectiveInitMinutes) { mutableStateOf(effectiveInitMinutes !in standardPresets) }
    var customMinutesInput by remember(effectiveInitMinutes) { mutableStateOf(effectiveInitMinutes.toString()) }

    val parsedCustomMinutes = customMinutesInput.toIntOrNull() ?: 0
    val isCustomInputInvalid = isCustomMode && (parsedCustomMinutes < 1 || parsedCustomMinutes > MAX_MINUTES_24_HOURS)

    val effectiveMinutes = if (isCustomMode) {
        parsedCustomMinutes.coerceIn(1, MAX_MINUTES_24_HOURS)
    } else {
        selectedPresetMinutes
    }

    LaunchedEffect(configuredMinutes) {
        val mins = configuredMinutes ?: return@LaunchedEffect
        if (mins != effectiveMinutes) {
            isCustomMode = mins !in standardPresets
            selectedPresetMinutes = mins
            customMinutesInput = mins.toString()
        }
    }

    val displaySeconds = if (timerState.isPomodoro) timerState.remainingSeconds else timerState.elapsedSeconds
    val formattedTime = DateUtils.formatTimerDisplay(displaySeconds)

    val progress = if (timerState.isPomodoro && timerState.totalSeconds > 0) {
        (timerState.totalSeconds - timerState.remainingSeconds).toFloat() / timerState.totalSeconds
    } else {
        1f
    }

    val isMinuteUnit = remember(habit.unit) {
        habit.unit.trim().lowercase() in setOf("min", "mins", "minuto", "minutos")
    }

    val targetText = remember(habit, initialMinutes, isMinuteUnit) {
        if (isMinuteUnit) {
            val target = habit.targetValue.toInt()
            if (initialMinutes > 0) {
                "Meta de hoy: $target min (faltan $initialMinutes min)"
            } else {
                "Meta de hoy: $target min (completada)"
            }
        } else {
            val formattedTarget = if (habit.targetValue % 1f == 0f) habit.targetValue.toInt().toString() else habit.targetValue.toString()
            "Meta: $formattedTarget ${habit.unit}"
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = IconHelper.getIconByName(habit.iconName),
                            contentDescription = null,
                            tint = habitColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header & Selectors
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector: Pomodoro vs Libre
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isPomodoroMode,
                        enabled = !hasActiveSession,
                        onClick = {
                            if (!isPomodoroMode) onChangeMode(true)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Pomodoro (Cuenta atrás)")
                    }
                    SegmentedButton(
                        selected = !isPomodoroMode,
                        enabled = !hasActiveSession,
                        onClick = {
                            if (isPomodoroMode) onChangeMode(false)
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
                                    onConfigureMinutes(mins)
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
                                            onConfigureMinutes(p)
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
                                    onConfigureMinutes(next)
                                },
                                label = { Text("+15m") }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            SuggestionChip(
                                onClick = {
                                    val next = (parsedCustomMinutes + 60).coerceIn(1, MAX_MINUTES_24_HOURS)
                                    customMinutesInput = next.toString()
                                    selectedPresetMinutes = next
                                    onConfigureMinutes(next)
                                },
                                label = { Text("+1h") }
                            )
                        }
                    }
                }
            }

            // Circular Timer
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val circleColor = if (timerState.isRunning) {
                    habitColor
                } else if (timerState.isPaused) {
                    habitColor.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.primary
                }

                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = circleColor,
                    strokeWidth = 11.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    strokeCap = StrokeCap.Round
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = if (displaySeconds >= 3600) 36.sp else 46.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val statusText = when {
                        timerState.isRunning -> "En sesión • sumando al hábito"
                        timerState.isPaused -> "En pausa • toca reproducir para continuar"
                        else -> "Listo para iniciar • no arrancará hasta que toques reproducir"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        lineHeight = 14.sp
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                        containerColor = if (timerState.isRunning) Color(0xFFF43F5E) else habitColor
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
}
