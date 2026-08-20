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
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first

class ConsistencyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        
        // 35 days = 5 weeks of 7 days (oldest to today)
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

        val totalActive = maxOf(1, activeHabits.size)
        val activeHabitsById = activeHabits.associateBy { it.id }
        val logsByDate = allLogs.filter { past35Days.contains(it.date) }.groupBy { it.date }

        // Compute exact completion ratio for each of the 35 days
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

        // 35-day grid: 5 columns (weeks) x 7 rows (days), GitHub style orientation, 4 exact levels
        val heatmapBitmap: Bitmap = WidgetBitmapUtils.createHeatmapGridBitmap(
            dailyRatios = dailyRatiosList,
            columns = 5,
            rows = 7,
            widthPx = 420,
            heightPx = 210,
            gapPx = 6f,
            cornerRadiusPx = 4f
        )

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "ANALYTICS")
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(ColorProvider(WidgetColors.Surface))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .clickable(actionStartActivity(mainIntent))
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Header: "Constancia" (13sp medium DarkOnSurface) ... "5 semanas" (11sp mutedText)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Constancia",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextPrimary),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Text(
                            text = "5 semanas",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.MutedText),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // 5x7 Heatmap Grid
                    Image(
                        provider = ImageProvider(heatmapBitmap),
                        contentDescription = "Mapa de constancia de 5 semanas",
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                    )
                }
            }
        }
    }
}


