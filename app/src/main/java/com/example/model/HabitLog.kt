package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_logs",
    indices = [
        Index(value = ["habitId", "date"], unique = true)
    ]
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val date: String, // Format: YYYY-MM-DD
    val value: Float = 1f,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class DayCompletionStat(
    val date: String,
    val completedCount: Int,
    val totalCount: Int,
    val completionRatio: Float
)
