package com.example.widget

import androidx.compose.ui.graphics.Color

object WidgetColors {
    val Background = Color(0xFF0F172A)
    val CardSurface = Color(0xFF1E293B)
    val SurfaceVariant = Color(0xFF334155)
    val Border = Color(0xFF334155)

    val Indigo = Color(0xFF6366F1)
    val Cyan = Color(0xFF06B6D4)
    val Rose = Color(0xFFF43F5E)
    val Emerald = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Purple = Color(0xFF8B5CF6)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Visual feedback & flash animation colors
    val EmeraldGlow = Color(0xFF34D399)
    val EmeraldBright = Color(0xFF10B981)
    val FeedbackFlashBg = Color(0xFF065F46)
    val FeedbackFlashBorder = Color(0xFF6EE7B7)
    val FeedbackUncheckFlash = Color(0xFF475569)

    // Heatmap levels (Deep Emerald palette on Dark Slate)
    val HeatmapEmpty = Color(0xFF334155)
    val HeatmapLevel1 = Color(0xFF065F46)
    val HeatmapLevel2 = Color(0xFF059669)
    val HeatmapLevel3 = Color(0xFF10B981)
    val HeatmapLevel4 = Color(0xFF34D399)

    /**
     * Maps a completion ratio (0.0f..1.0f) to the corresponding discrete intensity level or interpolated color.
     */
    fun getHeatmapColor(completedRatio: Float): Color {
        val clamped = completedRatio.coerceIn(0f, 1f)
        return when {
            clamped <= 0f -> HeatmapEmpty
            clamped <= 0.25f -> HeatmapLevel1
            clamped <= 0.50f -> HeatmapLevel2
            clamped <= 0.75f -> HeatmapLevel3
            else -> HeatmapLevel4
        }
    }

    /**
     * Interpolates color across the 4 intensity buckets smoothly for high-fidelity canvas rendering.
     */
    fun interpolateHeatmapColorInt(ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        if (r <= 0f) return 0xFF334155.toInt()
        return when {
            r <= 0.25f -> {
                val fraction = r / 0.25f
                lerpColorInt(0xFF065F46.toInt(), 0xFF059669.toInt(), fraction)
            }
            r <= 0.50f -> {
                val fraction = (r - 0.25f) / 0.25f
                lerpColorInt(0xFF059669.toInt(), 0xFF10B981.toInt(), fraction)
            }
            r <= 0.75f -> {
                val fraction = (r - 0.50f) / 0.25f
                lerpColorInt(0xFF10B981.toInt(), 0xFF34D399.toInt(), fraction)
            }
            else -> 0xFF34D399.toInt()
        }
    }

    private fun lerpColorInt(colorStart: Int, colorEnd: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = ((colorStart ushr 24 and 0xFF) + f * ((colorEnd ushr 24 and 0xFF) - (colorStart ushr 24 and 0xFF))).toInt()
        val r = ((colorStart ushr 16 and 0xFF) + f * ((colorEnd ushr 16 and 0xFF) - (colorStart ushr 16 and 0xFF))).toInt()
        val g = ((colorStart ushr 8 and 0xFF) + f * ((colorEnd ushr 8 and 0xFF) - (colorStart ushr 8 and 0xFF))).toInt()
        val b = ((colorStart and 0xFF) + f * ((colorEnd and 0xFF) - (colorStart and 0xFF))).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun getHeatmapColorByCount(count: Int): Color {
        return when {
            count <= 0 -> HeatmapEmpty
            count == 1 -> HeatmapLevel1
            count == 2 -> HeatmapLevel2
            count == 3 -> HeatmapLevel3
            else -> HeatmapLevel4
        }
    }
}
