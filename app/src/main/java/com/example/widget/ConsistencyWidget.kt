package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first

class ConsistencyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        
        // 35 days = 5 weeks of 7 days
        val past35Days = DateUtils.getPastNDaysDateStrings(35)
        
        val allLogs = try {
            repository.allLogs.first()
        } catch (_: Exception) {
            emptyList()
        }
        val activeHabits = try {
            repository.activeHabits.first()
        } catch (_: Exception) {
            emptyList()
        }

        val habitsWithStats = try {
            val today = DateUtils.getTodayDateString()
            repository.getHabitsWithStats(today).first()
        } catch (_: Exception) {
            emptyList()
        }

        val topStreak = habitsWithStats.maxOfOrNull { it.currentStreak } ?: 0

        val totalActive = maxOf(1, activeHabits.size)
        val activeHabitsById = activeHabits.associateBy { it.id }
        val logsByDate = allLogs.filter { past35Days.contains(it.date) }.groupBy { it.date }

        // Compute exact completion intensity for each of the 35 days
        val dailyRatiosList = past35Days.map { dateStr ->
            val dayLogs = logsByDate[dateStr] ?: emptyList()
            val completedCount = dayLogs.count { log ->
                val habit = activeHabitsById[log.habitId]
                habit != null && log.value >= habit.targetValue
            }
            if (activeHabits.isNotEmpty()) {
                (completedCount.toFloat() / totalActive).coerceIn(0f, 1f)
            } else if (completedCount > 0) {
                1f
            } else {
                0f
            }
        }

        val activeDaysCount = dailyRatiosList.count { it > 0f }
        val activePct = if (past35Days.isNotEmpty()) ((activeDaysCount.toFloat() / past35Days.size) * 100).toInt() else 0

        // 35-day grid: 5 columns (weeks) x 7 rows (days), beautifully proportioned and sharp
        val heatmapBitmap: Bitmap = WidgetBitmapUtils.createHeatmapGridBitmap(
            dailyRatios = dailyRatiosList,
            columns = 5,
            rows = 7,
            widthPx = 520,
            heightPx = 180,
            gapPx = 8f,
            cornerRadiusPx = 6f
        )

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "ANALYTICS")
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(ColorProvider(WidgetColors.CardSurface))
                    .padding(14.dp)
                    .clickable(actionStartActivity(mainIntent))
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Constancia",
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.Indigo),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Últimas 5 semanas",
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.TextSecondary),
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$activeDaysCount/35 días ($activePct%)",
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.Emerald),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (topStreak > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_flame),
                                        contentDescription = "Racha",
                                        modifier = GlanceModifier.size(12.dp)
                                    )
                                    Spacer(modifier = GlanceModifier.width(2.dp))
                                    Text(
                                        text = "$topStreak días racha",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.Amber),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // 35-Day Heatmap Grid Bitmap filling with balanced proportions
                    Image(
                        provider = ImageProvider(heatmapBitmap),
                        contentDescription = "Mapa de constancia de 35 días",
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(72.dp)
                    )

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Footer with 4-bucket intensity legend
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Menos",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextMuted),
                                fontSize = 10.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Box(
                            modifier = GlanceModifier
                                .size(8.dp)
                                .cornerRadius(2.dp)
                                .background(ColorProvider(WidgetColors.HeatmapEmpty))
                        ) {}
                        Spacer(modifier = GlanceModifier.width(3.dp))
                        Box(
                            modifier = GlanceModifier
                                .size(8.dp)
                                .cornerRadius(2.dp)
                                .background(ColorProvider(WidgetColors.HeatmapLevel1))
                        ) {}
                        Spacer(modifier = GlanceModifier.width(3.dp))
                        Box(
                            modifier = GlanceModifier
                                .size(8.dp)
                                .cornerRadius(2.dp)
                                .background(ColorProvider(WidgetColors.HeatmapLevel2))
                        ) {}
                        Spacer(modifier = GlanceModifier.width(3.dp))
                        Box(
                            modifier = GlanceModifier
                                .size(8.dp)
                                .cornerRadius(2.dp)
                                .background(ColorProvider(WidgetColors.HeatmapLevel3))
                        ) {}
                        Spacer(modifier = GlanceModifier.width(3.dp))
                        Box(
                            modifier = GlanceModifier
                                .size(8.dp)
                                .cornerRadius(2.dp)
                                .background(ColorProvider(WidgetColors.HeatmapLevel4))
                        ) {}
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = "Más",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextMuted),
                                fontSize = 10.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "Toca para ver estadísticas",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextMuted),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

