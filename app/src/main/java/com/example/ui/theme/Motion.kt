package com.example.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * HabitFlow Motion Tokens
 * Centralized spring specifications and duration guidelines for fluid, organic animations.
 */
object Motion {
    // Interactive feedback (buttons, cards, chips, press states)
    fun <T> springInteractive() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy, // ~0.75f
        stiffness = Spring.StiffnessMedium // ~1500f
    )

    // Smooth value changes (XP, streak counts, progress fractions)
    fun <T> springValues() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0f
        stiffness = Spring.StiffnessMediumLow // ~400f
    )

    // Expressive transitions (dialogs, expandable sheets, cards appearing)
    fun <T> springExpressive() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy, // 0.75f
        stiffness = Spring.StiffnessLow // ~200f
    )

    // Micro-interactions (toggle, checkmark, scale pop)
    fun <T> springPop() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )

    const val DURATION_MICRO_MS = 150
    const val DURATION_MEDIUM_MS = 250
    const val DURATION_EXPAND_MS = 300
    const val STAGGER_DELAY_MS = 35
}
