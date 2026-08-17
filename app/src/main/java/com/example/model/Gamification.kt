package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey
    val id: Int = 1,
    val xp: Int = 0,
    val level: Int = 1,
    val totalCheckIns: Int = 0,
    val bestStreakAllTime: Int = 0,
    val isHardcoreMode: Boolean = false,
    val unlockedBadgeIds: List<String> = emptyList(),
    val totalFocusMinutes: Int = 0,
    val lastActiveDate: String = ""
)

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val requiredXp: Int = 0,
    val requiredStreak: Int = 0,
    val requiredCompletions: Int = 0
)

val AllBadges = listOf(
    Badge(
        id = "first_step",
        title = "Primer Paso",
        description = "Completa tu primer hábito o tarea.",
        icon = "rocket_launch",
        requiredCompletions = 1
    ),
    Badge(
        id = "streak_3",
        title = "Chispa de Hábito",
        description = "Consigue una racha de 3 días seguidos.",
        icon = "local_fire_department",
        requiredStreak = 3
    ),
    Badge(
        id = "streak_7",
        title = "Guerrero Semanal",
        description = "Mantén una racha de 7 días consecutivos.",
        icon = "military_tech",
        requiredStreak = 7
    ),
    Badge(
        id = "streak_30",
        title = "Maestro de la Disciplina",
        description = "Alcanza una racha legendaria de 30 días.",
        icon = "workspace_premium",
        requiredStreak = 30
    ),
    Badge(
        id = "century_club",
        title = "Centurión",
        description = "Completa 100 check-ins en total.",
        icon = "stars",
        requiredCompletions = 100
    ),
    Badge(
        id = "focus_master",
        title = "Maestro del Enfoque",
        description = "Acumula más de 120 minutos de cronómetro o Pomodoro.",
        icon = "timer",
        requiredXp = 500
    ),
    Badge(
        id = "zen_mode",
        title = "Titán HabitFlow",
        description = "Alcanza el nivel 10 de consistencia.",
        icon = "diamond",
        requiredXp = 2000
    )
)
