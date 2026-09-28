package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Estado diario de una sub-rutina: una fila significa que la sub-rutina esta marcada en esa fecha.
 * Reemplaza el uso de SubTask.isCompleted y SubTask.date, que quedan como campos heredados.
 */
@Entity(
    tableName = "sub_task_logs",
    indices = [
        Index(value = ["subTaskId", "date"], unique = true),
        Index(value = ["habitId", "date"])
    ]
)
data class SubTaskLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subTaskId: Long,
    val habitId: Long,
    val date: String // YYYY-MM-DD
)
