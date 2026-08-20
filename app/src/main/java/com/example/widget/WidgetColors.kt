package com.example.widget

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOnSurface
import com.example.ui.theme.DarkOnSurfaceVariant
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant

object WidgetColors {
    // Shared widget surface token: DarkSurface (#1E293B) at ~90% opacity (0xE61E293B)
    val Surface = Color(0xE61E293B)
    val CardSurface = Surface
    val Background = DarkBackground
    val SurfaceVariant = DarkSurfaceVariant

    // Typography tokens
    val TextPrimary = DarkOnSurface // #F1F5F9
    val TextSecondary = DarkOnSurfaceVariant // #94A3B8
    val MutedText = Color(0xFF64748B)
    val TextMuted = MutedText

    // Accent tokens
    val Emerald = BrandAccent // #10B981
    val Amber = BrandAmber // #F59E0B
    val Indigo = BrandPrimary // #6366F1

    // Visual feedback & flash animation colors
    val EmeraldGlow = Color(0xFF34D399)
    val FeedbackUncheckFlash = Color(0xFF475569)

    // Heatmap exact 4 intensity levels
    // 0% -> Color(0xFF334155), 1-33% -> Color(0x5210B981), 34-66% -> Color(0x9E10B981), 67-100% -> #10B981
    val HeatmapLevel0 = Color(0xFF334155) // 0%
    val HeatmapLevel1 = Color(0x5210B981) // 1-33% (Emerald ~32% opacity)
    val HeatmapLevel2 = Color(0x9E10B981) // 34-66% (Emerald ~62% opacity)
    val HeatmapLevel3 = Color(0xFF10B981) // 67-100% (Emerald solid)

    // Aliases
    val HeatmapEmpty = HeatmapLevel0
    val HeatmapLevel4 = HeatmapLevel3

    /**
     * Maps a completion ratio (0.0f..1.0f) to the exact 4 intensity levels.
     */
    fun getHeatmapColor(ratio: Float): Color {
        val r = ratio.coerceIn(0f, 1f)
        return when {
            r <= 0f -> HeatmapLevel0
            r <= 0.33f -> HeatmapLevel1
            r <= 0.66f -> HeatmapLevel2
            else -> HeatmapLevel3
        }
    }

    /**
     * Exact 4-level color Int mapping for canvas rendering without interpolation.
     */
    fun getHeatmapColorInt(ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        return when {
            r <= 0f -> 0xFF334155.toInt()
            r <= 0.33f -> 0x5210B981.toInt()
            r <= 0.66f -> 0x9E10B981.toInt()
            else -> 0xFF10B981.toInt()
        }
    }

    fun interpolateHeatmapColorInt(ratio: Float): Int = getHeatmapColorInt(ratio)
}


