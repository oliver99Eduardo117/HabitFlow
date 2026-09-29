package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HabitWithStats
import com.example.model.SubTask
import com.example.util.IconHelper

/**
 * Hoja inferior que se abre al tocar una fila compacta: pasos, accion principal,
 * temporizador, editar y acceso al detalle completo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitQuickSheet(
    item: HabitWithStats,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onToggleSubTask: (SubTask, Boolean) -> Unit,
    onStartTimer: () -> Unit,
    onEdit: () -> Unit,
    onMore: () -> Unit
) {
    val habit = item.habit
    val habitColor = habitColorOf(habit.colorHex, MaterialTheme.colorScheme.primary)
    val state = habitActionState(item)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val steps = item.subTasks

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(habitColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconHelper.getIconByName(habit.iconName),
                        contentDescription = null,
                        tint = habitColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = habit.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    val details = listOfNotNull(
                        habit.reminderTime ?: "Todo el día",
                        habit.category.takeIf { it.isNotBlank() },
                        if (item.currentStreak > 0) "Racha de ${item.currentStreak} días" else null
                    )
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }

            // Avance del dia
            val progressText: String? = when {
                habit.unit.isNotEmpty() -> {
                    val value = item.todayLog?.value ?: 0f
                    "${formatHabitAmount(value)} de ${formatHabitAmount(habit.targetValue)} ${habit.unit}"
                }
                steps.isNotEmpty() -> "${steps.count { it.isCompleted }} de ${steps.size} pasos"
                else -> null
            }
            if (progressText != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (state.kind == HabitActionKind.DONE) HabitDoneAccent else habitColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            if (state.kind == HabitActionKind.BLOCKED) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Primero completa: ${item.blockingHabitTitle ?: "su requisito"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Pasos
            if (steps.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        steps.forEach { step ->
                            val stepEnabled = item.isDependencyMet || step.isCompleted
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = stepEnabled, role = Role.Checkbox) {
                                        onToggleSubTask(step, !step.isCompleted)
                                    }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = step.isCompleted,
                                    onCheckedChange = null,
                                    enabled = stepEnabled,
                                    colors = CheckboxDefaults.colors(checkedColor = habitColor)
                                )
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (step.isCompleted) muted else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (step.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }

            // Accion principal
            val primaryText = when (state.kind) {
                HabitActionKind.DONE -> if (habit.unit.isNotEmpty()) "Ajustar valor" else "Desmarcar"
                HabitActionKind.BLOCKED -> "Bloqueado por su requisito"
                HabitActionKind.QUANTITY -> "Registrar ${habit.unit}"
                HabitActionKind.CHECK -> when {
                    steps.isEmpty() -> "Completar"
                    steps.any { it.isCompleted } -> "Completar lo que falta"
                    else -> "Completar los ${steps.size} pasos"
                }
            }
            val isDone = state.kind == HabitActionKind.DONE
            val primaryContainer = if (isDone) MaterialTheme.colorScheme.surfaceVariant else habitColor
            val primaryContent = when {
                isDone -> MaterialTheme.colorScheme.onSurface
                habitColor.luminance() > 0.3f -> Color(0xFF111827)
                else -> Color.White
            }
            Button(
                onClick = onPrimaryAction,
                enabled = state.kind != HabitActionKind.BLOCKED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryContainer,
                    contentColor = primaryContent
                )
            ) {
                Text(text = primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            // Acciones secundarias
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (habit.hasTimer) {
                    OutlinedButton(
                        onClick = onStartTimer,
                        enabled = item.isDependencyMet,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Temporizador", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Editar", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onMore,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Más opciones")
                }
            }
        }
    }
}
