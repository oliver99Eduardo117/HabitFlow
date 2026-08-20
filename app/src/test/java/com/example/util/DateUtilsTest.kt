package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateUtilsTest {

    @Test
    fun `calculateStreak with empty set returns zero current and zero best streak`() {
        val result = DateUtils.calculateStreak(emptySet())
        assertEquals(Pair(0, 0), result)
    }

    @Test
    fun `calculateStreak with active streak including today`() {
        val today = DateUtils.getTodayDateString()
        val yesterday = DateUtils.getDaysAgoDateString(1)
        val twoDaysAgo = DateUtils.getDaysAgoDateString(2)

        val completedDates = setOf(today, yesterday, twoDaysAgo)
        val (currentStreak, bestStreak) = DateUtils.calculateStreak(completedDates)

        assertEquals(3, currentStreak)
        assertEquals(3, bestStreak)
    }

    @Test
    fun `calculateStreak with active streak starting yesterday when today is not yet completed`() {
        val yesterday = DateUtils.getDaysAgoDateString(1)
        val twoDaysAgo = DateUtils.getDaysAgoDateString(2)
        val threeDaysAgo = DateUtils.getDaysAgoDateString(3)

        val completedDates = setOf(yesterday, twoDaysAgo, threeDaysAgo)
        val (currentStreak, bestStreak) = DateUtils.calculateStreak(completedDates)

        assertEquals(3, currentStreak)
        assertEquals(3, bestStreak)
    }

    @Test
    fun `calculateStreak with broken streak prior to yesterday resets current streak to zero`() {
        val twoDaysAgo = DateUtils.getDaysAgoDateString(2)
        val threeDaysAgo = DateUtils.getDaysAgoDateString(3)
        val fourDaysAgo = DateUtils.getDaysAgoDateString(4)

        val completedDates = setOf(twoDaysAgo, threeDaysAgo, fourDaysAgo)
        val (currentStreak, bestStreak) = DateUtils.calculateStreak(completedDates)

        assertEquals(0, currentStreak)
        assertEquals(3, bestStreak)
    }

    @Test
    fun `calculateStreak with best historical streak greater than current streak`() {
        val today = DateUtils.getTodayDateString()
        val historicalStreak = setOf(
            "2024-01-01",
            "2024-01-02",
            "2024-01-03",
            "2024-01-04",
            "2024-01-05"
        )
        val completedDates = historicalStreak + setOf(today)
        val (currentStreak, bestStreak) = DateUtils.calculateStreak(completedDates)

        assertEquals(1, currentStreak)
        assertEquals(5, bestStreak)
    }

    @Test
    fun `calculateStreak with non consecutive dates`() {
        val dates = setOf(
            "2024-02-01",
            "2024-02-03",
            "2024-02-05",
            "2024-02-07"
        )
        val (currentStreak, bestStreak) = DateUtils.calculateStreak(dates)

        assertEquals(0, currentStreak)
        assertEquals(1, bestStreak)
    }

    @Test
    fun `calculateWeeklySuccessRate with empty habits returns zero percentage`() {
        val stats = DateUtils.calculateWeeklySuccessRate(emptyList(), emptyList())
        assertEquals(0, stats.overallPercentage)
        assertEquals(0, stats.totalCompletions)
        assertEquals(7, stats.dailyBreakdown.size)
    }

    @Test
    fun `calculateWeeklySuccessRate with full completions calculates 100 percent`() {
        val habit = com.example.model.Habit(id = 1L, title = "Agua", targetValue = 1f)
        val past7Days = DateUtils.getPastNDaysDateStrings(7)
        val logs = past7Days.map { date ->
            com.example.model.HabitLog(id = 0L, habitId = 1L, date = date, value = 1f)
        }

        val stats = DateUtils.calculateWeeklySuccessRate(listOf(habit), logs, past7Days)
        assertEquals(100, stats.overallPercentage)
        assertEquals(7, stats.totalCompletions)
        assertEquals(7, stats.perfectDaysCount)
        assertEquals(100, stats.bestDayPercentage)
    }

    @Test
    fun `calculateWeeklySuccessRate with partial completions`() {
        val habit1 = com.example.model.Habit(id = 1L, title = "Agua", targetValue = 1f)
        val habit2 = com.example.model.Habit(id = 2L, title = "Ejercicio", targetValue = 1f)
        val past7Days = DateUtils.getPastNDaysDateStrings(7)
        // Complete habit1 all 7 days, habit2 on 0 days -> 7 of 14 completed -> 50%
        val logs = past7Days.map { date ->
            com.example.model.HabitLog(id = 0L, habitId = 1L, date = date, value = 1f)
        }

        val stats = DateUtils.calculateWeeklySuccessRate(listOf(habit1, habit2), logs, past7Days)
        assertEquals(50, stats.overallPercentage)
        assertEquals(7, stats.totalCompletions)
        assertEquals(14, stats.totalScheduled)
    }

    @Test
    fun `calculateMonthlyTrend generates correct data points and calculations for month`() {
        val habit = com.example.model.Habit(id = 1L, title = "Meditación", targetValue = 1f)
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.AUGUST, 15)
        }
        val logs = listOf(
            com.example.model.HabitLog(id = 1L, habitId = 1L, date = "2026-08-01", value = 1f),
            com.example.model.HabitLog(id = 2L, habitId = 1L, date = "2026-08-02", value = 1f),
            com.example.model.HabitLog(id = 3L, habitId = 1L, date = "2026-08-03", value = 1f)
        )

        val stats = DateUtils.calculateMonthlyTrend(listOf(habit), logs, cal)
        assertEquals(31, stats.dataPoints.size)
        assertTrue(stats.hasData)
        assertEquals(3, stats.totalCompletions)
        assertEquals(100f, stats.peakRate, 0.01f)
    }

    @Test
    fun `calculateMonthlyTrend with filterHabitId filters logs appropriately`() {
        val habit1 = com.example.model.Habit(id = 1L, title = "Meditación", targetValue = 1f)
        val habit2 = com.example.model.Habit(id = 2L, title = "Correr", targetValue = 1f)
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.AUGUST, 15)
        }
        val logs = listOf(
            com.example.model.HabitLog(id = 1L, habitId = 1L, date = "2026-08-01", value = 1f),
            com.example.model.HabitLog(id = 2L, habitId = 2L, date = "2026-08-01", value = 1f),
            com.example.model.HabitLog(id = 3L, habitId = 2L, date = "2026-08-02", value = 1f)
        )

        val statsHabit1 = DateUtils.calculateMonthlyTrend(listOf(habit1, habit2), logs, cal, filterHabitId = 1L)
        assertEquals(1, statsHabit1.totalCompletions)

        val statsHabit2 = DateUtils.calculateMonthlyTrend(listOf(habit1, habit2), logs, cal, filterHabitId = 2L)
        assertEquals(2, statsHabit2.totalCompletions)
    }
}
