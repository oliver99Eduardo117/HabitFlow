package com.example.util

import org.junit.Assert.assertEquals
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
}
