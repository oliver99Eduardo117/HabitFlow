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

data class LevelTier(
    val level: Int,
    val title: String,
    val rank: String,
    val minXp: Int,
    val maxXp: Int,
    val iconName: String,
    val perk: String,
    val primaryColorHex: String,
    val secondaryColorHex: String
)

object GamificationConfig {
    // XP Rewards Values
    const val XP_HABIT_COMPLETION = 25
    const val XP_OVERACHIEVEMENT_BONUS = 15
    const val XP_SUBTASK_COMPLETION = 5
    const val XP_FOCUS_PER_MINUTE = 2
    const val XP_DAILY_STREAK_BONUS = 10
    const val HARDCORE_XP_MULTIPLIER = 1.25f

    val LevelTiers = listOf(
        LevelTier(
            level = 1,
            title = "Iniciado de Hábitos",
            rank = "Bronce I",
            minXp = 0,
            maxXp = 100,
            iconName = "sprout",
            perk = "Inicio de la aventura y primeras rutinas",
            primaryColorHex = "#94A3B8",
            secondaryColorHex = "#64748B"
        ),
        LevelTier(
            level = 2,
            title = "Aprendiz Constante",
            rank = "Bronce II",
            minXp = 100,
            maxXp = 250,
            iconName = "local_fire_department",
            perk = "Chispa de motivación y consistencia básica",
            primaryColorHex = "#CD7F32",
            secondaryColorHex = "#D97706"
        ),
        LevelTier(
            level = 3,
            title = "Practicante Disciplinado",
            rank = "Plata I",
            minXp = 250,
            maxXp = 450,
            iconName = "military_tech",
            perk = "Dominio de hábitos recurrentes semanales",
            primaryColorHex = "#94A3B8",
            secondaryColorHex = "#CBD5E1"
        ),
        LevelTier(
            level = 4,
            title = "Guerrero de la Rutina",
            rank = "Plata II",
            minXp = 450,
            maxXp = 700,
            iconName = "shield",
            perk = "Resistencia al aplazamiento y enfoque diario",
            primaryColorHex = "#38BDF8",
            secondaryColorHex = "#0284C7"
        ),
        LevelTier(
            level = 5,
            title = "Arquitecto de Hábitos",
            rank = "Oro I",
            minXp = 700,
            maxXp = 1050,
            iconName = "auto_awesome",
            perk = "Estructuración y optimización de bloques de tiempo",
            primaryColorHex = "#F59E0B",
            secondaryColorHex = "#D97706"
        ),
        LevelTier(
            level = 6,
            title = "Especialista en Productividad",
            rank = "Oro II",
            minXp = 1050,
            maxXp = 1500,
            iconName = "psychology",
            perk = "Integración fluida de tareas y hábitos en cadena",
            primaryColorHex = "#EAB308",
            secondaryColorHex = "#F97316"
        ),
        LevelTier(
            level = 7,
            title = "Titán del Enfoque",
            rank = "Platino I",
            minXp = 1500,
            maxXp = 2050,
            iconName = "bolt",
            perk = "Concentración profunda ininterrumpida y flujo",
            primaryColorHex = "#06B6D4",
            secondaryColorHex = "#3B82F6"
        ),
        LevelTier(
            level = 8,
            title = "Maestro de la Voluntad",
            rank = "Platino II",
            minXp = 2050,
            maxXp = 2750,
            iconName = "workspace_premium",
            perk = "Control férreo de hábitos y metas a largo plazo",
            primaryColorHex = "#8B5CF6",
            secondaryColorHex = "#6366F1"
        ),
        LevelTier(
            level = 9,
            title = "Gran Maestro Zen",
            rank = "Diamante",
            minXp = 2750,
            maxXp = 3600,
            iconName = "diamond",
            perk = "Armonía total de hábitos, salud y mente",
            primaryColorHex = "#EC4899",
            secondaryColorHex = "#8B5CF6"
        ),
        LevelTier(
            level = 10,
            title = "Leyenda Suprema HabitFlow",
            rank = "Maestro Legendario",
            minXp = 3600,
            maxXp = 5000,
            iconName = "stars",
            perk = "Consistencia legendaria de por vida",
            primaryColorHex = "#10B981",
            secondaryColorHex = "#06B6D4"
        )
    )

    fun calculateLevel(xp: Int): Int {
        for (tier in LevelTiers) {
            if (xp < tier.maxXp) {
                return tier.level
            }
        }
        // Beyond Level 10
        val extraXp = xp - 5000
        return 10 + (extraXp / 1000).coerceAtLeast(0)
    }

    fun getLevelTier(level: Int): LevelTier {
        return LevelTiers.find { it.level == level } ?: LevelTiers.last()
    }

    data class LevelProgress(
        val currentLevel: Int,
        val levelTitle: String,
        val rankName: String,
        val currentXp: Int,
        val minXpForLevel: Int,
        val maxXpForLevel: Int,
        val xpInCurrentLevel: Int,
        val xpNeededForNextLevel: Int,
        val progressFraction: Float,
        val perk: String,
        val primaryColorHex: String,
        val secondaryColorHex: String
    )

    fun getProgress(xp: Int): LevelProgress {
        val level = calculateLevel(xp)
        val tier = getLevelTier(level)

        val minXp: Int
        val maxXp: Int
        if (level <= 10) {
            minXp = tier.minXp
            maxXp = tier.maxXp
        } else {
            minXp = 5000 + (level - 11) * 1000
            maxXp = minXp + 1000
        }

        val xpInLevel = (xp - minXp).coerceAtLeast(0)
        val span = (maxXp - minXp).coerceAtLeast(1)
        val fraction = (xpInLevel.toFloat() / span).coerceIn(0f, 1f)
        val remaining = (maxXp - xp).coerceAtLeast(0)

        return LevelProgress(
            currentLevel = level,
            levelTitle = if (level > 10) "Leyenda Suprema Lv.$level" else tier.title,
            rankName = if (level > 10) "Maestro Legendario+" else tier.rank,
            currentXp = xp,
            minXpForLevel = minXp,
            maxXpForLevel = maxXp,
            xpInCurrentLevel = xpInLevel,
            xpNeededForNextLevel = remaining,
            progressFraction = fraction,
            perk = tier.perk,
            primaryColorHex = tier.primaryColorHex,
            secondaryColorHex = tier.secondaryColorHex
        )
    }
}

data class Badge(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val icon: String,
    val requiredXp: Int = 0,
    val requiredStreak: Int = 0,
    val requiredCompletions: Int = 0,
    val requiredFocusMinutes: Int = 0
)

val AllBadges = listOf(
    Badge(
        id = "first_step",
        title = "Primer Paso",
        category = "Inicios",
        description = "Completa tu primer hábito o tarea diaria.",
        icon = "rocket_launch",
        requiredCompletions = 1
    ),
    Badge(
        id = "streak_3",
        title = "Chispa de Hábito",
        category = "Rachas",
        description = "Consigue una racha de 3 días consecutivos.",
        icon = "local_fire_department",
        requiredStreak = 3
    ),
    Badge(
        id = "streak_7",
        title = "Guerrero Semanal",
        category = "Rachas",
        description = "Mantén una racha de 7 días ininterrumpidos.",
        icon = "military_tech",
        requiredStreak = 7
    ),
    Badge(
        id = "streak_14",
        title = "Fortaleza Quincenal",
        category = "Rachas",
        description = "Alcanza 14 días seguidos de constancia total.",
        icon = "shield",
        requiredStreak = 14
    ),
    Badge(
        id = "streak_30",
        title = "Maestro de la Disciplina",
        category = "Rachas",
        description = "Alcanza una racha legendaria de 30 días.",
        icon = "workspace_premium",
        requiredStreak = 30
    ),
    Badge(
        id = "century_club",
        title = "Centurión del Hábito",
        category = "Dedicatoria",
        description = "Completa 100 check-ins en total en la aplicación.",
        icon = "stars",
        requiredCompletions = 100
    ),
    Badge(
        id = "focus_apprentice",
        title = "Enfoque Profundo",
        category = "Pomodoro",
        description = "Acumula 60 minutos de concentración y Pomodoro.",
        icon = "timer",
        requiredFocusMinutes = 60
    ),
    Badge(
        id = "focus_master",
        title = "Maestro del Enfoque",
        category = "Pomodoro",
        description = "Acumula más de 180 minutos de concentración.",
        icon = "psychology",
        requiredFocusMinutes = 180
    ),
    Badge(
        id = "xp_500",
        title = "Acumulador de XP",
        category = "Experiencia",
        description = "Acumula tus primeros 500 puntos de experiencia.",
        icon = "bolt",
        requiredXp = 500
    ),
    Badge(
        id = "xp_1500",
        title = "Veterano de la Rutina",
        category = "Experiencia",
        description = "Alcanza 1,500 puntos de experiencia totales.",
        icon = "auto_awesome",
        requiredXp = 1500
    ),
    Badge(
        id = "zen_mode",
        title = "Titán HabitFlow",
        category = "Maestría",
        description = "Alcanza 3,000 XP y el rango Diamante de constancia.",
        icon = "diamond",
        requiredXp = 3000
    )
)

