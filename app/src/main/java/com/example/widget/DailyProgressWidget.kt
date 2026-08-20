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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first

class DailyProgressWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()
        val habitsWithStats = try {
            repository.getHabitsWithStats(today).first()
        } catch (_: Exception) {
            emptyList()
        }

        val totalHabits = habitsWithStats.size
        val completedHabits = habitsWithStats.count { it.isCompletedToday }
        val ratio = if (totalHabits > 0) completedHabits.toFloat() / totalHabits else 0f
        val percentage = (ratio * 100).toInt()

        val progressRingBitmap: Bitmap = WidgetBitmapUtils.createProgressRingBitmap(
            percentage = percentage,
            sizePx = 180,
            strokeWidthPx = 18f,
            trackColorInt = 0xFF334155.toInt(),
            progressColorInt = 0xFF6366F1.toInt(),
            completedColorInt = 0xFF10B981.toInt()
        )

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "TODAY")
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(ColorProvider(WidgetColors.CardSurface))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(mainIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progreso Hoy",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.Indigo),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    Box(
                        modifier = GlanceModifier.size(58.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(progressRingBitmap),
                            contentDescription = "Progreso: $percentage%",
                            modifier = GlanceModifier.size(58.dp)
                        )
                        Text(
                            text = "$percentage%",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextPrimary),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    Text(
                        text = if (totalHabits == 0) "Sin hábitos" else "$completedHabits de $totalHabits completados",
                        style = TextStyle(
                            color = ColorProvider(if (percentage == 100 && totalHabits > 0) WidgetColors.Emerald else WidgetColors.TextSecondary),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

