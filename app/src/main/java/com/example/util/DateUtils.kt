package com.example.util

import com.example.model.Habit
import com.example.model.HabitLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale

object DateUtils {
    private val localeSpanish = Locale.forLanguageTag("es-ES")

    private val isoFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"
    private val displayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", localeSpanish)
    private val shortMonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", localeSpanish)
    private val monthYearFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", localeSpanish)

    private fun Calendar.toLocalDate(): LocalDate {
        return try {
            val zoneId = timeZone?.toZoneId() ?: ZoneId.systemDefault()
            toInstant().atZone(zoneId).toLocalDate()
        } catch (_: Exception) {
            LocalDate.of(
                get(Calendar.YEAR),
                get(Calendar.MONTH) + 1,
                get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    fun getTodayDateString(): String {
        return LocalDate.now().format(isoFormat)
    }

    fun formatDateForDisplay(dateString: String): String {
        return try {
            val date = LocalDate.parse(dateString, isoFormat)
            date.format(displayFormat).replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            dateString
        }
    }

    fun formatMonthYear(calendar: Calendar): String {
        val localDate = calendar.toLocalDate()
        return localDate.format(monthYearFormat).replaceFirstChar { it.uppercase() }
    }

    fun getDayOfWeek(dateString: String): Int {
        return try {
            LocalDate.parse(dateString, isoFormat).dayOfWeek.value // Lun=1 .. Dom=7
        } catch (_: Exception) {
            1
        }
    }

    fun getDaysAgoDateString(daysAgo: Int): String {
        return LocalDate.now().minusDays(daysAgo.toLong()).format(isoFormat)
    }

    fun getPastNDaysDateStrings(count: Int): List<String> {
        val list = ArrayList<String>(count)
        var date = LocalDate.now().minusDays((count - 1).toLong())
        for (i in 0 until count) {
            list.add(date.format(isoFormat))
            date = date.plusDays(1)
        }
        return list
    }

    /**
     * Generates a grid of dates for the contribution/heatmap graph (weeks * 7 days).
     * Every week column starts on Monday (index 0) and ends on Sunday (index 6).
     */
    fun getHeatmapDateMatrix(weeks: Int = 18): List<List<String>> {
        val today = LocalDate.now()
        // Align to current week's Sunday (end of current ISO week)
        val endSunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val totalDays = weeks * 7
        var startDate = endSunday.minusDays((totalDays - 1).toLong())

        val matrix = ArrayList<List<String>>(weeks)
        for (w in 0 until weeks) {
            val weekDays = ArrayList<String>(7)
            for (d in 0 until 7) {
                weekDays.add(startDate.format(isoFormat))
                startDate = startDate.plusDays(1)
            }
            matrix.add(weekDays)
        }
        return matrix
    }

    fun calculateMonthPositionsForHabit(dateMatrix: List<List<String>>): List<Pair<String, Int>> {
        if (dateMatrix.isEmpty()) return emptyList()
        val positions = mutableListOf<Pair<String, Int>>()

        var lastMonth = ""
        var lastAddedWeek = -10

        dateMatrix.forEachIndexed { weekIndex, week ->
            val firstOfMonth = week.find { it.endsWith("-01") }
            val targetDay = firstOfMonth ?: week.getOrNull(3) ?: week.firstOrNull()
            val monthStr = if (targetDay != null) {
                try {
                    val d = LocalDate.parse(targetDay, isoFormat)
                    d.format(shortMonthFormat).replaceFirstChar { it.uppercase() }
                } catch (_: Exception) { "" }
            } else ""

            if (monthStr.isNotEmpty()) {
                if (weekIndex == 0) {
                    positions.add(Pair(monthStr, 0))
                    lastMonth = monthStr
                    lastAddedWeek = 0
                } else if (monthStr != lastMonth && (weekIndex - lastAddedWeek) >= 3) {
                    positions.add(Pair(monthStr, weekIndex))
                    lastMonth = monthStr
                    lastAddedWeek = weekIndex
                }
            }
        }
        return positions
    }

    /**
     * Generates all days of a given month for the interactive calendar view.
     */
    fun getDaysInMonth(year: Int, month: Int): List<CalendarDay> {
        val yearMonth = YearMonth.of(year, month + 1)
        val maxDays = yearMonth.lengthOfMonth()
        val firstDay = yearMonth.atDay(1)
        val firstDayOfWeek = firstDay.dayOfWeek.value // 1=Mon .. 7=Sun

        val days = mutableListOf<CalendarDay>()

        // Leading empty days from previous month
        for (i in 1 until firstDayOfWeek) {
            days.add(CalendarDay(dayNumber = 0, dateString = "", isCurrentMonth = false))
        }

        val todayString = getTodayDateString()

        for (day in 1..maxDays) {
            val dateStr = yearMonth.atDay(day).format(isoFormat)
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

        // If today is completed, start from today. If not, start from yesterday if completed.
        val startDateStr = when {
            completedDates.contains(todayStr) -> todayStr
            completedDates.contains(yesterdayStr) -> yesterdayStr
            else -> null
        }

        if (startDateStr != null) {
            var currentDate: LocalDate? = try {
                LocalDate.parse(startDateStr, isoFormat)
            } catch (_: Exception) {
                null
            }

            while (currentDate != null) {
                val checkStr = currentDate.format(isoFormat)
                if (completedDates.contains(checkStr)) {
                    currentStreak++
                    currentDate = currentDate.minusDays(1)
                } else {
                    break
                }
            }
        }

        // Calculate best streak across all history
        val sortedDates = completedDates.mapNotNull {
            try { LocalDate.parse(it, isoFormat) } catch (_: Exception) { null }
        }.sorted()

        var bestStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null

        for (date in sortedDates) {
            if (prevDate == null) {
                tempStreak = 1
            } else {
                val diffDays = ChronoUnit.DAYS.between(prevDate, date)
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
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
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

        val yearMonth = YearMonth.of(year, month + 1)
        val maxDays = yearMonth.lengthOfMonth()
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
            val dateStr = yearMonth.atDay(day).format(isoFormat)
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
