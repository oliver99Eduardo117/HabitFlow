package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.HabitWithStats

@Composable
fun QuantitativeLogDialog(
    habitWithStats: HabitWithStats,
    onDismiss: () -> Unit,
    onSaveProgress: (Float, String) -> Unit
) {
    val habit = habitWithStats.habit
    val currentVal = habitWithStats.todayLog?.value ?: 0f
    val targetVal = habit.targetValue

    var valueState by remember { mutableFloatStateOf(currentVal) }
    var notes by remember { mutableStateOf(habitWithStats.todayLog?.notes ?: "") }

    val isOverachieved = valueState > targetVal

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Registrar ${habit.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big Numeric Counter
                Text(
                    text = "${valueState.toInt()}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOverachieved) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${habit.unit} (Meta: ${targetVal.toInt()})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isOverachieved) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🚀 ¡Superación de meta! (+XP bonus)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stepper Buttons (-5, -1, +1, +5, +10)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { valueState = (valueState - 1f).coerceAtLeast(0f) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Restar")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = { valueState += 1f },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Sumar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick add chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 20, 30).forEach { inc ->
                        OutlinedButton(
                            onClick = { valueState += inc },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+$inc", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Reflexión del día") },
                    placeholder = { Text("Ej. Buen ritmo hoy, me sentí enfocado...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Save button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveProgress(valueState, notes)
                            onDismiss()
                        }
                    ) {
                        Text("Guardar Progreso")
                    }
                }
            }
        }
    }
}
