package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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

class ConsistencyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WidgetRepositoryProvider.getRepository(context)
        val today = DateUtils.getTodayDateString()

        val habitsWithStats = WidgetRepositoryProvider.getHabitsWithStatsCached(context, today)

        val allLogs = try {
            repository.allLogs.first()
        } catch (_: Exception) {
            emptyList()
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_target_tab", "ANALYTICS")
        }

        provideContent {
            val size = LocalSize.current
            val density = context.resources.displayMetrics.density

            val prefs = currentState<Preferences>()
            val selectedHabitId = prefs[longPreferencesKey("selected_habit_id")]

            val targetHabitWithStats = if (selectedHabitId != null) {
                habitsWithStats.find { it.habit.id == selectedHabitId }
            } else {
                null
            } ?: habitsWithStats.maxByOrNull { it.currentStreak }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(ColorProvider(WidgetColors.Surface))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .clickable(actionStartActivity(mainIntent))
            ) {
                if (targetHabitWithStats != null) {
                    val habit = targetHabitWithStats.habit
                    val habitLogs = allLogs.filter { it.habitId == habit.id }
                    val completedDates = habitLogs.filter { it.value >= habit.targetValue }.map { it.date }.toSet()
                    val (currentStreak, bestStreak) = DateUtils.calculateStreak(completedDates)
                    val totalActiveDays = completedDates.size

                    val weeks = if (size.width < 220.dp) 10 else 14
                    val dateMatrix = DateUtils.getHeatmapDateMatrix(weeks = weeks)
                    val monthPositions = DateUtils.calculateMonthPositionsForHabit(dateMatrix)

                    val habitColorInt = try {
                        android.graphics.Color.parseColor(habit.colorHex)
                    } catch (_: Exception) {
                        0xFF6366F1.toInt()
                    }
                    val habitComposeColor = Color(habitColorInt)

                    val targetHeatmapWidthPx = ((size.width.value - 20f) * density).toInt().coerceAtLeast(140)
                    val targetHeatmapHeightPx = ((size.height.value - 64f) * density).toInt().coerceAtLeast(50)

                    val uncompletedColorInt = 0x38334155
                    val heatmapBitmap: Bitmap = WidgetBitmapUtils.createHeatmapBitmap(
                        columns = weeks,
                        monthPositions = monthPositions,
                        targetWidthPx = targetHeatmapWidthPx,
                        targetHeightPx = targetHeatmapHeightPx,
                        cellColorProvider = { col, row ->
                            val dateStr = dateMatrix.getOrNull(col)?.getOrNull(row)
                            if (dateStr != null && completedDates.contains(dateStr)) {
                                habitColorInt
                            } else {
                                uncompletedColorInt
                            }
                        }
                    )

                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        // 1. Encabezado compacto: Avatar 28dp + Habit info
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconRes = WidgetIconHelper.getWidgetIconRes(habit.iconName)
                            Box(
                                modifier = GlanceModifier
                                    .size(28.dp)
                                    .cornerRadius(7.dp)
                                    .background(ColorProvider(habitComposeColor.copy(alpha = 0.20f))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (iconRes != null) {
                                    Image(
                                        provider = ImageProvider(iconRes),
                                        contentDescription = habit.title,
                                        colorFilter = ColorFilter.tint(ColorProvider(habitComposeColor)),
                                        modifier = GlanceModifier.size(18.dp)
                                    )
                                } else {
                                    Text(
                                        text = habit.title.take(1).uppercase(),
                                        style = TextStyle(
                                            color = ColorProvider(habitComposeColor),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(6.dp))

                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = habit.title,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextPrimary),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = habit.category,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = ColorProvider(WidgetColors.TextSecondary),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(3.dp))

                        // 2. Fila de 3 tarjetas de stats compactas con iconos tematicos
                        Row(
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            // Stat 1: Racha (Llama Amber)
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .cornerRadius(6.dp)
                                    .background(ColorProvider(WidgetColors.CardSurface))
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_flame),
                                        contentDescription = "Racha",
                                        colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.Amber)),
                                        modifier = GlanceModifier.size(12.dp)
                                    )
                                    Text(
                                        text = "$currentStreak d",
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Racha",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 6.5.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(3.dp))

                            // Stat 2: Mejor Racha (Trofeo Amber)
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .cornerRadius(6.dp)
                                    .background(ColorProvider(WidgetColors.CardSurface))
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_trophy),
                                        contentDescription = "Mejor",
                                        colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.Amber)),
                                        modifier = GlanceModifier.size(12.dp)
                                    )
                                    Text(
                                        text = "$bestStreak d",
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Mejor",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 6.5.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(3.dp))

                            // Stat 3: Total Días (Calendario Check con color del hábito)
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .cornerRadius(6.dp)
                                    .background(ColorProvider(WidgetColors.CardSurface))
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_calendar_check),
                                        contentDescription = "Total",
                                        colorFilter = ColorFilter.tint(ColorProvider(habitComposeColor)),
                                        modifier = GlanceModifier.size(12.dp)
                                    )
                                    Text(
                                        text = "$totalActiveDays d",
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextPrimary),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Total",
                                        style = TextStyle(
                                            color = ColorProvider(WidgetColors.TextSecondary),
                                            fontSize = 6.5.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(3.dp))

                        // 3. Heatmap expandido
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(heatmapBitmap),
                                contentDescription = "Mapa de constancia de ${habit.title}",
                                contentScale = ContentScale.Fit,
                                modifier = GlanceModifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    // Empty state
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configura este widget desde el picker de widgets",
                            maxLines = 3,
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.TextSecondary),
                                fontSize = 12.sp,
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


