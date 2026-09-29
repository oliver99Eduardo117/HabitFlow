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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first

class ConsistencyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()

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
            putExtra("widget_target_tab", "ANALYTICS")
        }

        provideContent {
            val habitsWithStats by WidgetRepositoryProvider.habitsWithStatsFlow(context, today)
                .collectAsState(initial = initialHabits)
            val allLogs by repository.allLogs.collectAsState(initial = initialLogs)

            val size = LocalSize.current
            val density = context.resources.displayMetrics.density

            val prefs = currentState<Preferences>()
            val selectedHabitId = prefs[longPreferencesKey("selected_habit_id")]

            val targetHabitWithStats = if (selectedHabitId != null) {
                habitsWithStats.find { it.habit.id == selectedHabitId }
            } else {
                habitsWithStats.maxByOrNull { it.currentStreak }
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .cornerRadius(WidgetDimens.ContainerRadius)
                    .background(ColorProvider(WidgetColors.Container))
                    .padding(WidgetDimens.ContainerPadding)
                    .clickable(actionStartActivity(mainIntent))
            ) {
                if (targetHabitWithStats != null) {
                    val habit = targetHabitWithStats.habit
                    val currentStreak = targetHabitWithStats.currentStreak
                    val bestStreak = targetHabitWithStats.bestStreak
                    val totalCompletions = targetHabitWithStats.totalCompletions

                    val habitColorInt = parseHabitColorInt(habit.colorHex)
                    val habitComposeColor = Color(habitColorInt)

                    val innerW = size.width.value - 28f
                    val gridH = size.height.value - 28f - 28f - 8f
                    val cellDp = ((gridH - 14f - 12f) / 7f).toInt().toFloat().coerceIn(8f, 16f)
                    val weeks = ((innerW + 2f) / (cellDp + 2f)).toInt().coerceIn(6, 30)
                    val dateMatrix = DateUtils.getHeatmapDateMatrix(weeks = weeks)
                    val monthPositions = WidgetDates.monthPositions(dateMatrix)
                    val valueByDate = allLogs.filter { it.habitId == habit.id }.associate { it.date to it.value }
                    val todayCell: Pair<Int, Int>? = run {
                        var found: Pair<Int, Int>? = null
                        dateMatrix.forEachIndexed { col, week ->
                            val row = week.indexOf(today)
                            if (row >= 0) found = col to row
                        }
                        found
                    }

                    val heatmapBitmap: Bitmap = remember(valueByDate, habitColorInt, weeks, cellDp, density) {
                        WidgetBitmapUtils.createHeatmapGridBitmap(
                            columns = weeks,
                            monthPositions = monthPositions,
                            cellSizePx = cellDp * density,
                            gapPx = 2f * density,
                            monthLabelTextPx = 10f * density,
                            highlightCell = todayCell
                        ) { col, row ->
                            val date = dateMatrix.getOrNull(col)?.getOrNull(row) ?: return@createHeatmapGridBitmap 0
                            if (date > today) 0
                            else {
                                val ratio = if (habit.targetValue > 0f) (valueByDate[date] ?: 0f) / habit.targetValue else 0f
                                WidgetColors.habitIntensityColorInt(habitColorInt, ratio)
                            }
                        }
                    }

                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconRes = WidgetIconHelper.getWidgetIconRes(habit.iconName)
                            Box(
                                modifier = GlanceModifier
                                    .size(28.dp)
                                    .cornerRadius(8.dp)
                                    .background(ColorProvider(habitComposeColor.copy(alpha = 0.18f))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (iconRes != null) {
                                    Image(
                                        provider = ImageProvider(iconRes),
                                        contentDescription = habit.title,
                                        colorFilter = ColorFilter.tint(ColorProvider(habitComposeColor)),
                                        modifier = GlanceModifier.size(16.dp)
                                    )
                                } else {
                                    Text(
                                        text = habit.title.take(1).uppercase(),
                                        style = TextStyle(
                                            color = ColorProvider(habitComposeColor),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .padding(start = 8.dp)
                            ) {
                                Text(
                                    text = habit.title,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextPrimary),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = if (size.width.value >= 300f) {
                                        "$totalCompletions días completados"
                                    } else {
                                        "$totalCompletions días"
                                    },
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_flame),
                                    contentDescription = "Racha",
                                    modifier = GlanceModifier.size(13.dp)
                                )
                                Text(
                                    text = "$currentStreak",
                                    modifier = GlanceModifier.padding(start = 3.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.Amber),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (size.width.value >= 300f) {
                                    Text(
                                        text = "racha",
                                        modifier = GlanceModifier.padding(start = 3.dp),
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = GlanceModifier.padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_trophy),
                                    contentDescription = "Récord",
                                    colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.TextSecondary)),
                                    modifier = GlanceModifier.size(13.dp)
                                )
                                Text(
                                    text = "$bestStreak",
                                    modifier = GlanceModifier.padding(start = 3.dp),
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextPrimary),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (size.width.value >= 300f) {
                                    Text(
                                        text = "récord",
                                        modifier = GlanceModifier.padding(start = 3.dp),
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(heatmapBitmap),
                                contentDescription = "Constancia de ${habit.title}",
                                contentScale = ContentScale.Fit,
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    val emptyMessage = if (habitsWithStats.isEmpty()) {
                        "Crea un hábito en la app para ver su constancia"
                    } else {
                        "El hábito de este widget ya no está activo. Mantén presionado el widget para reconfigurarlo."
                    }
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = emptyMessage,
                            maxLines = 4,
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
