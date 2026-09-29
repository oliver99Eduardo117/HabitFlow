@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HabitWithStats
import com.example.util.IconHelper

/** Que hace y como se ve el boton circular principal de un habito. */
enum class HabitActionKind { DONE, BLOCKED, QUANTITY, CHECK }

data class HabitActionState(val kind: HabitActionKind, val progress: Float)

/** Verde de "hecho" con contraste suficiente para una palomita blanca. */
val HabitDoneFill = Color(0xFF047857)
val HabitDoneAccent = Color(0xFF10B981)

/** Naranja de la llama de racha; el mismo tono que en el Mapa de calor. */
val HabitStreakColor = Color(0xFFEA580C)

fun habitActionState(item: HabitWithStats): HabitActionState {
    val habit = item.habit
    if (item.isCompletedToday) return HabitActionState(HabitActionKind.DONE, 1f)
    if (!item.isDependencyMet) return HabitActionState(HabitActionKind.BLOCKED, 0f)
    if (habit.unit.isNotEmpty()) {
        val value = item.todayLog?.value ?: 0f
        val progress = if (habit.targetValue > 0f) (value / habit.targetValue).coerceIn(0f, 1f) else 0f
        return HabitActionState(HabitActionKind.QUANTITY, progress)
    }
    val steps = item.subTasks
    val progress = if (steps.isNotEmpty()) steps.count { it.isCompleted }.toFloat() / steps.size else 0f
    return HabitActionState(HabitActionKind.CHECK, progress)
}

/** Texto para el lector de pantalla de la accion principal. */
fun habitActionLabel(item: HabitWithStats): String {
    val habit = item.habit
    return when (habitActionState(item).kind) {
        HabitActionKind.DONE ->
            if (habit.unit.isNotEmpty()) "${habit.title}: hecho. Ajustar valor" else "${habit.title}: hecho. Desmarcar"
        HabitActionKind.BLOCKED ->
            "${habit.title}: bloqueado. Primero completa ${item.blockingHabitTitle ?: "su requisito"}"
        HabitActionKind.QUANTITY -> "Registrar ${habit.unit} de ${habit.title}"
        HabitActionKind.CHECK ->
            if (item.subTasks.isNotEmpty()) "Completar los pasos que faltan de ${habit.title}" else "Completar ${habit.title}"
    }
}

fun formatHabitAmount(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

fun habitColorOf(colorHex: String, fallback: Color): Color =
    try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        fallback
    }

/**
 * Boton circular de 48dp: el anillo se llena con el avance del dia y el icono
 * central indica la accion (palomita, mas o candado).
 */
@Composable
fun HabitActionRing(
    state: HabitActionState,
    color: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = state.kind != HabitActionKind.BLOCKED
    val track = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "habit_ring_progress"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 3.dp.toPx()
            val inset = strokePx / 2f + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            when (state.kind) {
                HabitActionKind.DONE -> drawCircle(color = HabitDoneFill)
                HabitActionKind.BLOCKED -> drawCircle(
                    color = muted.copy(alpha = 0.6f),
                    radius = size.minDimension / 2f - inset,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    )
                )
                else -> {
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
        Icon(
            imageVector = when (state.kind) {
                HabitActionKind.DONE, HabitActionKind.CHECK -> Icons.Default.Check
                HabitActionKind.BLOCKED -> Icons.Default.Lock
                HabitActionKind.QUANTITY -> Icons.Default.Add
            },
            contentDescription = null,
            tint = when (state.kind) {
                HabitActionKind.DONE -> Color.White
                HabitActionKind.BLOCKED -> muted
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(if (state.kind == HabitActionKind.BLOCKED) 18.dp else 22.dp)
        )
    }
}

/**
 * Un dato de la fila: icono + texto.
 * [color] pinta icono y texto (Hecho, bloqueado); [iconTint] pinta solo el icono (racha).
 */
private data class MetaPart(
    val icon: ImageVector,
    val text: String,
    val color: Color? = null,
    val iconTint: Color? = null
)

/**
 * Fila compacta de un habito para Linea de tiempo y Kanban.
 * Tocar la fila abre el detalle rapido; el boton circular hace la accion principal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitCompactRow(
    item: HabitWithStats,
    showTime: Boolean,
    onPrimaryAction: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = item.habit
    val habitColor = habitColorOf(habit.colorHex, MaterialTheme.colorScheme.primary)
    val state = habitActionState(item)
    val isDone = state.kind == HabitActionKind.DONE
    val isBlocked = state.kind == HabitActionKind.BLOCKED
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val errorText = MaterialTheme.colorScheme.error

    val meta = buildList {
        if (showTime) add(MetaPart(Icons.Default.Schedule, habit.reminderTime ?: "Todo el día"))
        if (habit.unit.isNotEmpty()) {
            val value = item.todayLog?.value ?: 0f
            add(
                MetaPart(
                    Icons.Default.TrackChanges,
                    "${formatHabitAmount(value)} / ${formatHabitAmount(habit.targetValue)} ${habit.unit}"
                )
            )
        } else if (item.subTasks.isNotEmpty()) {
            add(MetaPart(Icons.Default.Checklist, "${item.subTasks.count { it.isCompleted }} de ${item.subTasks.size} pasos"))
        }
        when {
            isDone -> add(MetaPart(Icons.Default.CheckCircle, "Hecho", color = HabitDoneAccent))
            isBlocked -> add(
                MetaPart(Icons.Default.Lock, "Primero: ${item.blockingHabitTitle ?: "su requisito"}", color = errorText)
            )
            // Con racha en cero no se muestra nada, para no cargar la fila
            item.currentStreak > 0 -> add(
                MetaPart(
                    Icons.Default.LocalFireDepartment,
                    if (item.currentStreak == 1) "1 día" else "${item.currentStreak} días",
                    iconTint = HabitStreakColor
                )
            )
        }
    }

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDone) HabitDoneAccent.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_row_${habit.id}")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background((if (isBlocked) muted else habitColor).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconHelper.getIconByName(habit.iconName),
                    contentDescription = null,
                    tint = if (isBlocked) muted else habitColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = habit.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isDone || isBlocked) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (meta.isNotEmpty()) {
                    // Los datos bajan de linea en lugar de aplastarse; cada uno lleva su icono
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        meta.forEach { part ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = part.icon,
                                    contentDescription = null,
                                    tint = part.iconTint ?: part.color ?: muted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = part.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = part.color ?: muted,
                                    fontWeight = if (part.color != null) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (state.kind == HabitActionKind.QUANTITY && state.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = habitColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            HabitActionRing(
                state = state,
                color = habitColor,
                label = habitActionLabel(item),
                onClick = onPrimaryAction
            )
        }
    }
}
