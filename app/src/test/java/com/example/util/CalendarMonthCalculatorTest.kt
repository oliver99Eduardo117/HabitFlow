package com.example.util

import com.example.model.Habit
import com.example.model.HabitLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CalendarMonthCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val september = YearMonth.of(2026, 9) // el 1 de septiembre de 2026 es martes
    private val today = LocalDate.of(2026, 9, 29)

    private fun millisOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun habit(
        id: Long,
        days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
        target: Float = 1f,
        createdOn: LocalDate = LocalDate.of(2026, 8, 1)
    ) = Habit(
        id = id,
        title = "H$id",
        frequencyDays = days,
        targetValue = target,
        createdAt = millisOf(createdOn)
    )

    private fun log(habitId: Long, day: Int, value: Float = 1f) =
        HabitLog(habitId = habitId, date = september.atDay(day).toString(), value = value)

    private fun build(habits: List<Habit>, logs: List<HabitLog>, month: YearMonth = september) =
        CalendarMonthCalculator.build(habits, logs, month, today, zone)

    @Test
    fun `month layout starts on monday and has one cell per day`() {
        val result = build(listOf(habit(1)), emptyList())
        assertEquals(1, result.leadingBlanks)
        assertEquals(30, result.days.size)
        assertEquals(6, build(listOf(habit(1)), emptyList(), YearMonth.of(2026, 2)).leadingBlanks)
    }

    @Test
    fun `day is perfect only when every scheduled habit reaches its goal`() {
        val habits = listOf(habit(1), habit(2, target = 8f))
        val logs = listOf(log(1, 10), log(2, 10, 8f), log(1, 11), log(2, 11, 5f))
        val result = build(habits, logs)
        assertEquals(CalendarDayKind.PERFECT, result.days[9].kind)
        assertEquals(CalendarDayKind.PARTIAL, result.days[10].kind)
        assertEquals(1, result.days[10].completedCount)
        assertEquals(2, result.days[10].scheduledCount)
    }

    @Test
    fun `partial progress alone is partial, not done`() {
        val result = build(listOf(habit(1, target = 20f)), listOf(log(1, 15, 8f)))
        val day = result.days[14]
        assertEquals(CalendarDayKind.PARTIAL, day.kind)
        assertEquals(0, day.completedCount)
        assertEquals(0f, day.ratio, 0.0001f)
    }

    @Test
    fun `habit that does not fall on that weekday is not counted`() {
        // Lunes, miercoles y viernes. El martes 15 solo cuenta el habito diario.
        val habits = listOf(habit(1), habit(2, days = listOf(1, 3, 5)))
        val result = build(habits, listOf(log(1, 15)))
        val tuesday = result.days[14]
        assertEquals(1, tuesday.scheduledCount)
        assertEquals(CalendarDayKind.PERFECT, tuesday.kind)
        assertEquals(setOf(1L), tuesday.scheduledHabitIds)
    }

    @Test
    fun `done on a day it was not scheduled still counts as done`() {
        val habits = listOf(habit(2, days = listOf(1, 3, 5)))
        val result = build(habits, listOf(log(2, 15)))
        assertEquals(1, result.days[14].scheduledCount)
        assertEquals(CalendarDayKind.PERFECT, result.days[14].kind)
    }

    @Test
    fun `missed free and future days`() {
        val habits = listOf(habit(1, days = listOf(1, 2, 3, 4, 5)))
        val result = build(habits, emptyList())
        assertEquals(CalendarDayKind.MISSED, result.days[13].kind) // lunes 14
        assertEquals(CalendarDayKind.FREE, result.days[12].kind) // domingo 13
        assertEquals(CalendarDayKind.FUTURE, result.days[29].kind) // miercoles 30
        assertEquals(1, result.days[29].scheduledCount)
    }

    @Test
    fun `today without progress stays partial because the day is still open`() {
        val result = build(listOf(habit(1)), emptyList())
        val day = result.days[28]
        assertTrue(day.isToday)
        assertEquals(CalendarDayKind.PARTIAL, day.kind)
    }

    @Test
    fun `days before the habit was created do not count`() {
        val habits = listOf(habit(1, createdOn = LocalDate.of(2026, 9, 20)))
        val result = build(habits, emptyList())
        assertEquals(CalendarDayKind.FREE, result.days[18].kind) // dia 19
        assertEquals(CalendarDayKind.MISSED, result.days[19].kind) // dia 20
    }

    @Test
    fun `logs of habits outside the list are ignored`() {
        val result = build(listOf(habit(1)), listOf(log(99, 10), log(1, 10)))
        assertEquals(1, result.days[9].scheduledCount)
        assertEquals(CalendarDayKind.PERFECT, result.days[9].kind)
    }

    @Test
    fun `streak counts perfect days ending today and skips free days`() {
        // Lunes a sabado; el domingo 27 es libre y no corta la racha
        val habits = listOf(habit(1, days = listOf(1, 2, 3, 4, 5, 6)))
        val logs = listOf(log(1, 24), log(1, 25), log(1, 26), log(1, 28))
        val open = build(habits, logs)
        assertEquals(4, open.perfectStreak) // hoy (29) sigue abierto: no suma ni corta
        val closed = build(habits, logs + log(1, 29))
        assertEquals(5, closed.perfectStreak)
        assertEquals(0, build(habits, listOf(log(1, 25), log(1, 26))).perfectStreak) // hueco el 28
    }

    @Test
    fun `month stats ignore future days`() {
        val habits = listOf(habit(1))
        val logs = (1..29).filter { it % 2 == 1 }.map { log(1, it) } // 15 de 29 dias
        val result = build(habits, logs)
        assertEquals(15, result.perfectDays)
        assertEquals(52, result.completionPercent) // 15 / 29 = 51.7
    }

    @Test
    fun `no habits means free days and zero stats`() {
        val result = build(emptyList(), emptyList())
        assertTrue(result.days.filter { !it.date.isAfter(today) }.all { it.kind == CalendarDayKind.FREE })
        assertEquals(0, result.perfectStreak)
        assertEquals(0, result.completionPercent)
        assertFalse(result.days.any { it.kind == CalendarDayKind.PERFECT })
    }

    @Test
    fun `dayOf finds the day only inside the month`() {
        val result = build(listOf(habit(1)), emptyList())
        assertEquals(LocalDate.of(2026, 9, 5), result.dayOf(LocalDate.of(2026, 9, 5))?.date)
        assertEquals(null, result.dayOf(LocalDate.of(2026, 10, 1)))
    }
}
