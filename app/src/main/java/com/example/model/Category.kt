package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey
    val name: String,
    val colorHex: String = "#6366F1",
    val iconName: String = "category",
    val isDefault: Boolean = false
)

val DefaultCategories = listOf(
    Category(name = "Productividad", colorHex = "#6366F1", iconName = "work", isDefault = true),
    Category(name = "Fitness & Salud", colorHex = "#10B981", iconName = "fitness", isDefault = true),
    Category(name = "Mente & Zen", colorHex = "#06B6D4", iconName = "meditation", isDefault = true),
    Category(name = "Estudio & Dev", colorHex = "#8B5CF6", iconName = "code", isDefault = true),
    Category(name = "Finanzas", colorHex = "#F59E0B", iconName = "payments", isDefault = true),
    Category(name = "Rutina Personal", colorHex = "#EC4899", iconName = "person", isDefault = true)
)

data class HabitTemplate(
    val title: String,
    val description: String,
    val category: String,
    val colorHex: String,
    val iconName: String,
    val unit: String = "",
    val targetValue: Float = 1f,
    val hasTimer: Boolean = false,
    val timerDurationMinutes: Int = 25,
    val subTasks: List<String> = emptyList()
)

val HabitTemplates = listOf(
    HabitTemplate(
        title = "Rutina Matutina Imparable",
        description = "Comienza el día con máxima energía e intención.",
        category = "Rutina Personal",
        colorHex = "#F59E0B",
        iconName = "wb_sunny",
        subTasks = listOf("Vaso de agua con limón", "5 minutos de estiramientos", "Planificar 3 prioridades del día")
    ),
    HabitTemplate(
        title = "Sesión de Enfoque Profundo",
        description = "Bloque de trabajo sin distracciones ni redes.",
        category = "Productividad",
        colorHex = "#6366F1",
        iconName = "timer",
        unit = "min",
        targetValue = 60f,
        hasTimer = true,
        timerDurationMinutes = 60
    ),
    HabitTemplate(
        title = "Meditación & Respiración",
        description = "Calma mental y presencia plena de 15 minutos.",
        category = "Mente & Zen",
        colorHex = "#06B6D4",
        iconName = "self_improvement",
        unit = "min",
        targetValue = 15f,
        hasTimer = true,
        timerDurationMinutes = 15
    ),
    HabitTemplate(
        title = "Entrenamiento / Gimnasio",
        description = "Ejercicio físico de fuerza o cardio.",
        category = "Fitness & Salud",
        colorHex = "#10B981",
        iconName = "fitness_center",
        unit = "min",
        targetValue = 45f,
        hasTimer = true,
        timerDurationMinutes = 45
    ),
    HabitTemplate(
        title = "Lectura Productiva",
        description = "Leer 20 páginas de libros de crecimiento o técnicos.",
        category = "Estudio & Dev",
        colorHex = "#8B5CF6",
        iconName = "menu_book",
        unit = "páginas",
        targetValue = 20f
    ),
    HabitTemplate(
        title = "Beber 2.5L de Agua",
        description = "Mantener una óptima hidratación a lo largo del día.",
        category = "Fitness & Salud",
        colorHex = "#38BDF8",
        iconName = "water_drop",
        unit = "vasos",
        targetValue = 8f
    ),
    HabitTemplate(
        title = "Commit & Código Diario",
        description = "Avanzar en proyectos de programación o aprender un nuevo concepto.",
        category = "Estudio & Dev",
        colorHex = "#10B981",
        iconName = "terminal",
        unit = "commits",
        targetValue = 1f
    )
)
