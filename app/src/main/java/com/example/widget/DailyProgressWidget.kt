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
        val today = DateUtils.getTodayDateString()
        val habitsWithStats = WidgetRepositoryProvider.getHabitsWithStatsCached(context, today)

        val totalHabits = habitsWithStats.size
        val completedHabits = habitsWithStats.count { it.isCompletedToday }
        val ratio = if (totalHabits > 0) completedHabits.toFloat() / totalHabits else 0f
        val percentage = (ratio * 100).toInt()

        val progressRingBitmap: Bitmap = WidgetBitmapUtils.createProgressRingBitmap(
            percentage = percentage,
            sizePx = 210,
            strokeWidthPx = 18f,
            trackColorInt = 0xFF334155.toInt(),
            progressColorInt = 0xFF6366F1.toInt(),
            completedColorInt = 0xFF6366F1.toInt()
        )

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "TODAY")
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(ColorProvider(WidgetColors.Surface))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .clickable(actionStartActivity(mainIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular ring (70dp diameter) with percentage (22sp semibold) inside
                    Box(
                        modifier = GlanceModifier.size(70.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(progressRingBitmap),
                            contentDescription = "Progreso: $percentage%",
                            modifier = GlanceModifier.size(70.dp)
                        )
                        Text(
                            text = "$percentage%",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextPrimary),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Text below ring: "X de Y hábitos" (12sp DarkOnSurfaceVariant)
                    Text(
                        text = "$completedHabits de $totalHabits hábitos",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.TextSecondary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}


