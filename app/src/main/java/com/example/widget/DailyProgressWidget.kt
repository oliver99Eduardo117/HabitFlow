package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first

class DailyProgressWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = DateUtils.getTodayDateString()
        val initialHabits = try {
            WidgetRepositoryProvider.habitsWithStatsFlow(context, today).first()
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
            val todayHabits = habitsWithStats.forToday()

            val total = todayHabits.size
            val completed = todayHabits.count { it.isCompletedToday }
            val allDone = total > 0 && completed == total
            val next = todayHabits.firstOrNull { !it.isCompletedToday && it.isDependencyMet }

            val size = LocalSize.current
            val density = LocalContext.current.resources.displayMetrics.density
            val ringDp = minOf(
                size.width.value - 28f,
                size.height.value - 28f - (if (size.height.value >= 130f) 28f else 0f)
            ).coerceIn(56f, 96f)

            val segmentColors = when {
                total == 0 -> emptyList()
                allDone -> List(total) { 0xFF10B981.toInt() }
                else -> todayHabits.map { item ->
                    if (item.isCompletedToday) parseHabitColorInt(item.habit.colorHex) else 0xFF334155.toInt()
                }
            }

            val ringBitmap: Bitmap = remember(segmentColors, ringDp) {
                WidgetBitmapUtils.createSegmentedRingBitmap(
                    segmentColors = segmentColors,
                    sizePx = (ringDp * density).toInt(),
                    strokeWidthPx = ringDp * 0.083f * density
                )
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .cornerRadius(WidgetDimens.ContainerRadius)
                    .background(ColorProvider(WidgetColors.Container))
                    .padding(WidgetDimens.ContainerPadding)
                    .clickable(actionStartActivity(mainIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier.size(ringDp.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(ringBitmap),
                            contentDescription = "$completed de $total hábitos",
                            modifier = GlanceModifier.size(ringDp.dp)
                        )
                        when {
                            total == 0 -> {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_calendar_check),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.TextSecondary)),
                                    modifier = GlanceModifier.size(24.dp)
                                )
                            }
                            allDone -> {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_check),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.EmeraldText)),
                                    modifier = GlanceModifier.size((ringDp * 0.35f).dp)
                                )
                            }
                            else -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$completed",
                                            style = TextStyle(
                                                color = ColorProvider(WidgetColors.TextPrimary),
                                                fontSize = if (ringDp >= 80f) 28.sp else 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = "/$total",
                                            style = TextStyle(
                                                color = ColorProvider(WidgetColors.TextSecondary),
                                                fontSize = if (ringDp >= 80f) 16.sp else 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                    if (ringDp >= 80f) {
                                        Text(
                                            text = "hechos",
                                            style = TextStyle(
                                                color = ColorProvider(WidgetColors.TextSecondary),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (size.height.value >= 130f) {
                        when {
                            total == 0 -> {
                                Text(
                                    text = "Sin hábitos hoy",
                                    modifier = GlanceModifier.padding(top = 10.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            allDone -> {
                                Text(
                                    text = "Día completo",
                                    modifier = GlanceModifier.padding(top = 10.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.EmeraldText),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            next != null -> {
                                val nextColor = Color(parseHabitColorInt(next.habit.colorHex))
                                val iconRes = WidgetIconHelper.getWidgetIconRes(next.habit.iconName)
                                Row(
                                    modifier = GlanceModifier.padding(top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sigue:",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 12.sp
                                        )
                                    )
                                    Box(modifier = GlanceModifier.padding(start = 5.dp)) {
                                        if (iconRes != null) {
                                            Image(
                                                provider = ImageProvider(iconRes),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(ColorProvider(nextColor)),
                                                modifier = GlanceModifier.size(13.dp)
                                            )
                                        } else {
                                            Text(
                                                text = next.habit.title.take(1).uppercase(),
                                                style = TextStyle(
                                                    color = ColorProvider(nextColor),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = next.habit.title,
                                        maxLines = 1,
                                        modifier = GlanceModifier.padding(start = 4.dp),
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
