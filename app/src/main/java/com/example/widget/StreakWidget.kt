package com.example.widget

import android.content.Context
import android.content.Intent
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
import com.example.R
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first

class StreakWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()
        val habitsWithStats = try {
            repository.getHabitsWithStats(today).first()
        } catch (_: Exception) {
            emptyList()
        }

        val topStreakHabit = habitsWithStats.maxByOrNull { it.currentStreak }
        val hasActiveStreak = topStreakHabit != null && topStreakHabit.currentStreak > 0

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
                if (hasActiveStreak && topStreakHabit != null) {
                    // Normal state: habit name (11sp) -> streak number (32sp bold) -> "días" (11sp) -> flame (17dp)
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = topStreakHabit.habit.title,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextSecondary),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = GlanceModifier.height(2.dp))

                        Text(
                            text = "${topStreakHabit.currentStreak}",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextPrimary),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = GlanceModifier.height(2.dp))

                        Text(
                            text = "días",
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextSecondary),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = GlanceModifier.height(3.dp))

                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_flame),
                            contentDescription = "Racha",
                            modifier = GlanceModifier.size(17.dp)
                        )
                    }
                } else {
                    // Empty state: flame (20dp, muted) -> 8dp -> "Completa un hábito para empezar tu racha" (11.5sp)
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_flame_muted),
                            contentDescription = "Sin racha activa",
                            modifier = GlanceModifier.size(20.dp)
                        )

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        Text(
                            text = "Completa un hábito para empezar tu racha",
                            maxLines = 2,
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextSecondary),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }
}


