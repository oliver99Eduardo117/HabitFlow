package com.example.util

import com.example.model.Habit
import com.example.model.HabitLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
    private val shortMonthFormat = SimpleDateFormat("MMM", Locale("es", "ES"))
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))

    fun getTodayDateString(): String {
        return isoFormat.format(Date())
    }

    fun formatDateForDisplay(dateString: String): String {
        return try {
            val date = isoFormat.parse(dateString) ?: Date()
            displayFormat.format(date).replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            dateString
        }
    }

    fun formatMonthYear(calendar: Calendar): String {
        return monthYearFormat.format(calendar.time).replaceFirstChar { it.uppercase() }
    }

    fun getDayOfWeek(dateString: String): Int {
        return try {
            val date = isoFormat.parse(dateString) ?: Date()
            val cal = Calendar.getInstance().apply { time = date }
            var day = cal.get(Calendar.DAY_OF_WEEK) - 1 // 1=Sun in Java, let's map: Mon=1, Tue=2.. Sun=7
            if (day == 0) day = 7
            day
        } catch (_: Exception) {
            1
        }
    }

    fun getDaysAgoDateString(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return isoFormat.format(cal.time)
    }

    fun getPastNDaysDateStrings(count: Int): List<String> {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(count - 1))
        for (i in 0 until count) {
            list.add(isoFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    /**
     * Generates a grid of dates for the contribution/heatmap graph (weeks * 7 days).
     * Every week column starts on Monday (index 0) and ends on Sunday (index 6).
     */
    fun getHeatmapDateMatrix(weeks: Int = 18): List<List<String>> {
        val cal = Calendar.getInstance()
        // Align to current week's Sunday (end of current ISO week)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysUntilSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else (Calendar.SATURDAY - dayOfWeek + 1)
        cal.add(Calendar.DAY_OF_YEAR, daysUntilSunday)

        val totalDays = weeks * 7
        cal.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))

        val matrix = mutableListOf<MutableList<String>>()
        for (w in 0 until weeks) {
            val weekDays = mutableListOf<String>()
            for (d in 0 until 7) {
                weekDays.add(isoFormat.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            matrix.add(weekDays)
        }
        return matrix
    }

    /**
     * Generates all days of a given month for the interactive calendar view.
     */
    fun getDaysInMonth(year: Int, month: Int): List<CalendarDay> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val days = mutableListOf<CalendarDay>()
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // 1=Mon .. 7=Sun
        var firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        if (firstDayOfWeek == 0) firstDayOfWeek = 7

        // Leading empty days from previous month
        for (i in 1 until firstDayOfWeek) {
            days.add(CalendarDay(dayNumber = 0, dateString = "", isCurrentMonth = false))
        }

        val todayString = getTodayDateString()

        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = isoFormat.format(cal.time)
            days.add(
                CalendarDay(
                    dayNumber = day,
                    dateString = dateStr,
                    isCurrentMonth = true,
                    isToday = (dateStr == todayString)
                )
            )
        }
        return days
    }

    fun calculateStreak(completedDates: Set<String>): Pair<Int, Int> {
        if (completedDates.isEmpty()) return Pair(0, 0)
        
        val todayStr = getTodayDateString()
        val yesterdayStr = getDaysAgoDateString(1)
        
        var currentStreak = 0
        var checkCal = Calendar.getInstance()

        // If today is completed, start from today. If not, start from yesterday if completed.
        val startDate = when {
            completedDates.contains(todayStr) -> todayStr
            completedDates.contains(yesterdayStr) -> yesterdayStr
            else -> null
        }

        if (startDate != null) {
            val startCal = Calendar.getInstance()
            try {
                val d = isoFormat.parse(startDate)
                if (d != null) startCal.time = d
            } catch (_: Exception) {}

            while (true) {
                val checkStr = isoFormat.format(startCal.time)
                if (completedDates.contains(checkStr)) {
                    currentStreak++
                    startCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
        }

        // Calculate best streak across all history
        val sortedDates = completedDates.mapNotNull {
            try { isoFormat.parse(it) } catch (_: Exception) { null }
        }.sorted()

        var bestStreak = 0
        var tempStreak = 0
        var prevDate: Date? = null

        for (date in sortedDates) {
            if (prevDate == null) {
                tempStreak = 1
            } else {
                val diffDays = (date.time - prevDate.time) / (1000 * 60 * 60 * 24)
                if (diffDays == 1L) {
                    tempStreak++
                } else if (diffDays > 1L) {
                    tempStreak = 1
                }
            }
            if (tempStreak > bestStreak) {
                bestStreak = tempStreak
            }
            prevDate = date
        }

        return Pair(currentStreak, maxOf(currentStreak, bestStreak))
    }

    fun formatTimerDisplay(totalSeconds: Int): String {
        val safeSeconds = maxOf(0, totalSeconds)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatMinutesReadable(totalMinutes: Int): String {
        val safeMinutes = maxOf(1, totalMinutes)
        val hours = safeMinutes / 60
        val remainingMinutes = safeMinutes % 60
        return when {
            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
            hours > 0 -> "${hours}h (${safeMinutes}m)"
            else -> "${safeMinutes} min"
        }
    }

    /**
     * Calculates the weekly success rate, daily breakdown, and comparison metrics for the past 7 days.
     */
    fun calculateWeeklySuccessRate(
        habits: List<Habit>,
        allLogs: List<HabitLog>,
        past7Days: List<String> = getPastNDaysDateStrings(7)
    ): WeeklySuccessStats {
        val previous7Days = getPastNDaysDateStrings(14).take(7)
        val logsByDateAndHabit = allLogs.groupBy { it.date }
        val habitsById = habits.associateBy { it.id }

        val dailyBreakdown = past7Days.map { dateStr ->
            val dayOfWeekNum = getDayOfWeek(dateStr) // 1=Mon .. 7=Sun
            val dayLabel = when (dayOfWeekNum) {
                1 -> "Lun"
                2 -> "Mar"
                3 -> "Mié"
                4 -> "Jue"
                5 -> "Vie"
                6 -> "Sáb"
                else -> "Dom"
            }
            val fullDayName = when (dayOfWeekNum) {
                1 -> "Lunes"
                2 -> "Martes"
                3 -> "Miércoles"
                4 -> "Jueves"
                5 -> "Viernes"
                6 -> "Sábado"
                else -> "Domingo"
            }

            // Scheduled habits for this day (based on frequencyDays or all active if frequencyDays is empty)
            val scheduledHabits = habits.filter { habit ->
                habit.frequencyDays.isEmpty() || habit.frequencyDays.contains(dayOfWeekNum)
            }
            val scheduledCount = maxOf(1, scheduledHabits.size)

            val dateLogs = logsByDateAndHabit[dateStr] ?: emptyList()
            val completedCount = dateLogs.count { log ->
                val habit = habitsById[log.habitId]
                habit != null && log.value >= habit.targetValue
            }

            val successRate = if (scheduledHabits.isNotEmpty()) {
                (completedCount.toFloat() / scheduledCount * 100f).coerceIn(0f, 100f)
            } else if (completedCount > 0) {
                100f
            } else {
                0f
            }

            DaySuccessData(
                dateString = dateStr,
                dayLabel = dayLabel,
                fullDayName = fullDayName,
                dayOfWeekNum = dayOfWeekNum,
                completedCount = completedCount,
                scheduledCount = scheduledCount,
                successRate = successRate
            )
        }

        val totalCompletions = dailyBreakdown.sumOf { it.completedCount }
        val totalScheduled = dailyBreakdown.sumOf { it.scheduledCount }
        val overallPercentage = if (totalScheduled > 0 && habits.isNotEmpty()) {
            ((totalCompletions.toFloat() / totalScheduled) * 100f).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val perfectDaysCount = dailyBreakdown.count { it.successRate >= 100f && it.completedCount > 0 }
        val bestDay = dailyBreakdown.filter { it.completedCount > 0 }.maxByOrNull { it.successRate }
        val bestDayLabel = bestDay?.fullDayName ?: if (dailyBreakdown.isNotEmpty()) dailyBreakdown.last().fullDayName else "N/A"
        val bestDayPercentage = bestDay?.successRate?.toInt() ?: 0

        // Calculate previous 7 days percentage
        var prevCompletions = 0
        var prevScheduled = 0
        previous7Days.forEach { dateStr ->
            val dayOfWeekNum = getDayOfWeek(dateStr)
            val scheduledCount = maxOf(1, habits.count { it.frequencyDays.isEmpty() || it.frequencyDays.contains(dayOfWeekNum) })
            val completedCount = logsByDateAndHabit[dateStr]?.count { log ->
                val habit = habitsById[log.habitId]
                habit != null && log.value >= habit.targetValue
            } ?: 0
            prevCompletions += completedCount
            prevScheduled += scheduledCount
        }
        val previousWeekPercentage = if (prevScheduled > 0 && habits.isNotEmpty()) {
            ((prevCompletions.toFloat() / prevScheduled) * 100f).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val percentageDelta = overallPercentage - previousWeekPercentage

        return WeeklySuccessStats(
            overallPercentage = overallPercentage,
            totalCompletions = totalCompletions,
            totalScheduled = totalScheduled,
            perfectDaysCount = perfectDaysCount,
            bestDayLabel = bestDayLabel,
            bestDayPercentage = bestDayPercentage,
            previousWeekPercentage = previousWeekPercentage,
            percentageDelta = percentageDelta,
            dailyBreakdown = dailyBreakdown
        )
    }

    /**
     * Calculates the monthly compliance trend data points for a line chart visualization.
     */
    fun calculateMonthlyTrend(
        habits: List<Habit>,
        allLogs: List<HabitLog>,
        calendar: Calendar = Calendar.getInstance(),
        filterHabitId: Long? = null
    ): MonthlyTrendStats {
        val targetHabits = if (filterHabitId != null) {
            habits.filter { it.id == filterHabitId }
        } else {
            habits
        }

        val habitsById = targetHabits.associateBy { it.id }
        val logsByDate = allLogs
            .filter { filterHabitId == null || it.habitId == filterHabitId }
            .groupBy { it.date }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val monthTitle = formatMonthYear(calendar)

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayStr = getTodayDateString()

        val dataPoints = mutableListOf<MonthlyTrendDataPoint>()
        var sumRate = 0f
        var elapsedDaysCount = 0
        var totalCompletions = 0
        var totalScheduled = 0
        var peakRate = 0f
        var peakDay = 1
        var lowestRate = 100f

        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = isoFormat.format(cal.time)
            val isToday = (dateStr == todayStr)
            val isFuture = dateStr > todayStr
            val dayOfWeekNum = getDayOfWeek(dateStr)

            val scheduledForDay = if (targetHabits.isEmpty()) {
                0
            } else {
                targetHabits.count { it.frequencyDays.isEmpty() || it.frequencyDays.contains(dayOfWeekNum) }
            }

            val completedForDay = logsByDate[dateStr]?.count { log ->
                val habit = habitsById[log.habitId]
                habit != null && log.value >= habit.targetValue
            } ?: 0

            val rate = if (scheduledForDay > 0) {
                ((completedForDay.toFloat() / scheduledForDay) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            dataPoints.add(
                MonthlyTrendDataPoint(
                    dayNumber = day,
                    dateString = dateStr,
                    dayOfWeek = dayOfWeekNum,
                    completedCount = completedForDay,
                    scheduledCount = scheduledForDay,
                    completionRate = rate,
                    isFuture = isFuture,
                    isToday = isToday
                )
            )

            if (!isFuture) {
                elapsedDaysCount++
                sumRate += rate
                totalCompletions += completedForDay
                totalScheduled += scheduledForDay
                if (rate >= peakRate) {
                    peakRate = rate
                    peakDay = day
                }
                if (rate < lowestRate) {
                    lowestRate = rate
                }
            }
        }

        val averageRate = if (elapsedDaysCount > 0) {
            sumRate / elapsedDaysCount
        } else {
            0f
        }

        if (lowestRate > 100f) lowestRate = 0f

        return MonthlyTrendStats(
            monthName = monthTitle,
            year = year,
            month = month,
            dataPoints = dataPoints,
            averageRate = averageRate,
            peakRate = peakRate,
            peakDay = peakDay,
            lowestRate = lowestRate,
            totalCompletions = totalCompletions,
            totalScheduled = totalScheduled,
            hasData = targetHabits.isNotEmpty() && elapsedDaysCount > 0
        )
    }
}

data class MonthlyTrendDataPoint(
    val dayNumber: Int,
    val dateString: String,
    val dayOfWeek: Int,
    val completedCount: Int,
    val scheduledCount: Int,
    val completionRate: Float, // 0f..100f
    val isFuture: Boolean,
    val isToday: Boolean
)

data class MonthlyTrendStats(
    val monthName: String,
    val year: Int,
    val month: Int,
    val dataPoints: List<MonthlyTrendDataPoint>,
    val averageRate: Float,
    val peakRate: Float,
    val peakDay: Int,
    val lowestRate: Float,
    val totalCompletions: Int,
    val totalScheduled: Int,
    val hasData: Boolean
)

data class DaySuccessData(
    val dateString: String,
    val dayLabel: String,
    val fullDayName: String,
    val dayOfWeekNum: Int,
    val completedCount: Int,
    val scheduledCount: Int,
    val successRate: Float // 0f..100f
)

data class WeeklySuccessStats(
    val overallPercentage: Int, // 0..100
    val totalCompletions: Int,
    val totalScheduled: Int,
    val perfectDaysCount: Int,
    val bestDayLabel: String,
    val bestDayPercentage: Int,
    val previousWeekPercentage: Int,
    val percentageDelta: Int, // positive or negative
    val dailyBreakdown: List<DaySuccessData>
)

data class CalendarDay(
    val dayNumber: Int,
    val dateString: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean = false,
    val completionRatio: Float = 0f
)
