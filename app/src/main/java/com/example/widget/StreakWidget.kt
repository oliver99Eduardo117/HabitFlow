package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class StreakWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = DateUtils.getTodayDateString()
        val repository = WidgetRepositoryProvider.getRepository(context)
        val initialHabits = try {
            WidgetRepositoryProvider.habitsWithStatsFlow(context, today).first()
        } catch (_: Exception) {
            emptyList()
        }
        val initialLogs = try {
            repository.allLogs.first()
        } catch (_: Exception) {
            emptyList()
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "TODAY")
        }

        provideContent {
            val habitsWithStats by WidgetRepositoryProvider.habitsWithStatsFlow(context, today)
                .collectAsState(initial = initialHabits)
            val allLogs by repository.allLogs.collectAsState(initial = initialLogs)

            val size = LocalSize.current
            val density = LocalContext.current.resources.displayMetrics.density

            val topStreakHabit = habitsWithStats.maxByOrNull { it.currentStreak }
            val hasActiveStreak = topStreakHabit != null && topStreakHabit.currentStreak > 0

            val baseModifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(WidgetDimens.ContainerRadius)
                .background(ColorProvider(WidgetColors.Container))
                .padding(WidgetDimens.ContainerPadding)
                .clickable(actionStartActivity(mainIntent))

            val containerModifier = if (hasActiveStreak && topStreakHabit != null) {
                baseModifier.semantics {
                    contentDescription = "Racha de ${topStreakHabit.currentStreak} días en ${topStreakHabit.habit.title}"
                }
            } else {
                baseModifier
            }

            Box(
                modifier = containerModifier,
                contentAlignment = Alignment.Center
            ) {
                if (hasActiveStreak && topStreakHabit != null) {
                    val top = topStreakHabit
                    val habit = top.habit
                    val doneToday = top.isCompletedToday
                    val scheduledToday = top.isScheduled
                    val pendingToday = scheduledToday && !doneToday

                    val completedDates = allLogs
                        .filter { it.habitId == habit.id && it.value >= habit.targetValue }
                        .map { it.date }
                        .toSet()
                    val todayDate = LocalDate.now()
                    val strip = (6 downTo 0).map { back ->
                        val d = todayDate.minusDays(back.toLong())
                        val dow = d.dayOfWeek.value
                        val scheduled = habit.frequencyDays.isEmpty() || dow in habit.frequencyDays
                        val state = when {
                            d.toString() in completedDates -> StripDay.DONE
                            back == 0 && scheduled -> StripDay.TODAY_PENDING
                            !scheduled -> StripDay.REST
                            else -> StripDay.MISSED
                        }
                        StripDayState(WidgetDates.DAY_LETTERS[dow - 1], state, back == 0)
                    }
                    val widthPx = ((size.width.value - 28f) * density).toInt()
                    val stripBitmap = remember(strip, widthPx) {
                        WidgetBitmapUtils.createWeekStripBitmap(strip, widthPx, density)
                    }

                    val habitColor = Color(parseHabitColorInt(habit.colorHex))
                    val iconRes = WidgetIconHelper.getWidgetIconRes(habit.iconName)

                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (iconRes != null) {
                                Image(
                                    provider = ImageProvider(iconRes),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(ColorProvider(habitColor)),
                                    modifier = GlanceModifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = habit.title.take(1).uppercase(),
                                    style = TextStyle(
                                        color = ColorProvider(habitColor),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = habit.title,
                                maxLines = 1,
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .padding(start = 5.dp),
                                style = TextStyle(
                                    color = ColorProvider(WidgetColors.TextPrimary),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            if (size.width.value >= 130f) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_trophy),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.TextSecondary)),
                                    modifier = GlanceModifier.size(12.dp)
                                )
                                Text(
                                    text = "${top.bestStreak}",
                                    modifier = GlanceModifier.padding(start = 3.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Column(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(
                                        if (pendingToday) R.drawable.ic_widget_flame_muted else R.drawable.ic_widget_flame
                                    ),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(28.dp)
                                )
                                Text(
                                    text = "${top.currentStreak}",
                                    modifier = GlanceModifier.padding(start = 4.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextPrimary),
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            when {
                                pendingToday -> {
                                    Text(
                                        text = "Pendiente hoy",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.Amber),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                !scheduledToday && !doneToday -> {
                                    Text(
                                        text = "Hoy descansa",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "días seguidos",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        if (size.height.value >= 140f) {
                            Image(
                                provider = ImageProvider(stripBitmap),
                                contentDescription = "Últimos 7 días",
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_flame_muted),
                            contentDescription = "Sin racha activa",
                            modifier = GlanceModifier.size(24.dp)
                        )
                        Text(
                            text = "Completa un hábito para empezar tu racha",
                            maxLines = 2,
                            modifier = GlanceModifier.padding(top = 8.dp),
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextSecondary),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }
}
