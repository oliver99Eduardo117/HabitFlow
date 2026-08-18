package com.example.util

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
}

data class CalendarDay(
    val dayNumber: Int,
    val dateString: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean = false,
    val completionRatio: Float = 0f
)
