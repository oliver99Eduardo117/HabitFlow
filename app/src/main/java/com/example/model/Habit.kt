package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "General",
    val colorHex: String = "#6366F1",
    val iconName: String = "check_circle",
    val frequencyDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon, 7=Sun
    val isArchived: Boolean = false,
    val orderIndex: Int = 0,
    val unit: String = "", // e.g. "min", "páginas", "vasos", "km", "reps" or empty for check
    val targetValue: Float = 1f,
    val progressiveIncrease: Float = 0f, // auto increase target by this amount every 7 streak
    val hasTimer: Boolean = false,
    val timerDurationMinutes: Int = 25,
    val reminderTime: String? = null, // e.g. "08:30"
    val reminderMinutesAdvance: Int = 0, // e.g. 0 (exact time), 5, 10, 15, 30, 60 minutes before
    val reminderCustomMessage: String? = null, // Custom motivational text
    val parentHabitId: Long? = null, // For sub-habits or routine nested tasks
    val dependencyHabitId: Long? = null, // Prerequisite habit that must be completed first
    val lastMilestoneStreakClaimed: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ViewLayoutMode {
    LIST,
    HEATMAP,
    KANBAN,
    TIMELINE
}

data class HabitWithStats(
    val habit: Habit,
    val todayLog: HabitLog? = null,
    val isCompletedToday: Boolean = false,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCompletions: Int = 0,
    val subTasks: List<SubTask> = emptyList(),
    val isDependencyMet: Boolean = true
)
