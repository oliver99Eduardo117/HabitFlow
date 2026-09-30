package com.example.widget

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DarkOnSurface
import com.example.ui.theme.DarkOnSurfaceVariant

object WidgetColors {
    // Typography tokens
    val TextPrimary = DarkOnSurface // #F1F5F9
    val TextSecondary = DarkOnSurfaceVariant // #94A3B8

    // Accent tokens
    val Emerald = BrandAccent // #10B981
    val Amber = BrandAmber // #F59E0B

    val Container = Color(0xF00F172A)      // fondo del widget, #0F172A al 94%
    val Card = Color(0xFF1E293B)           // tarjeta interna
    val Track = Color(0xFF334155)          // pistas, barras y celdas vacías
    val EmeraldText = Color(0xFF34D399)    // texto e icono de "completo" sobre fondo oscuro
    val EmeraldSoft = Color(0x2910B981)    // fondo de la pastilla "Día completo" (16%)
    val RestDot = Color(0xFF475569)        // día de descanso
    val CheckOnLight = Color(0xFF0F172A)   // check sobre colores de hábito claros

    fun getHeatmapColorInt(ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        return when {
            r <= 0f -> 0xFF334155.toInt()
            r < 0.34f -> 0x4D10B981
            r < 0.67f -> 0x8C10B981.toInt()
            r < 1f -> 0xCC10B981.toInt()
            else -> 0xFF10B981.toInt()
        }
    }

    /** Intensidad en el color del hábito: vacío, 28%, 52%, 76%, 100%. */
    fun habitIntensityColorInt(habitColorInt: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        if (r <= 0f) return 0xFF1E293B.toInt()
        val alpha = when {
            r < 0.34f -> 0x47
            r < 0.67f -> 0x85
            r < 1f -> 0xC2
            else -> 0xFF
        }
        return (habitColorInt and 0x00FFFFFF) or (alpha shl 24)
    }
}
