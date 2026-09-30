package com.example.util

import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.UserStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ProgressCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 29) // martes
    private val allDays = listOf(1, 2, 3, 4, 5, 6, 7)

    private fun millisOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun habit(
        id: Long,
        days: List<Int> = allDays,
        target: Float = 1f,
        createdOn: LocalDate = LocalDate.of(2026, 6, 1),
        title: String = "H$id"
    ) = Habit(
        id = id,
        title = title,
        frequencyDays = days,
        targetValue = target,
        createdAt = millisOf(createdOn),
        orderIndex = id.toInt()
    )

    private fun log(habitId: Long, date: LocalDate, value: Float = 1f) =
        HabitLog(habitId = habitId, date = date.toString(), value = value)

    private fun daysAgo(n: Long): LocalDate = today.minusDays(n)

    private fun build(
        habits: List<Habit>,
        logs: List<HabitLog>,
        stats: UserStats = UserStats(),
        archived: List<Habit> = emptyList(),
        on: LocalDate = today
    ) = ProgressCalculator.build(habits, archived, logs, stats, on, zone)

    @Test
    fun `pending today does not lower the week percent`() {
        val h = habit(1)
        val lastSix = (1L..6L).map { log(1, daysAgo(it)) }
        val open = build(listOf(h), lastSix).week
        assertEquals(6, open.completed)
        assertEquals(6, open.scheduled)
        assertEquals(100, open.percent)

        val closed = build(listOf(h), lastSix + log(1, today)).week
        assertEquals(7, closed.completed)
        assertEquals(7, closed.scheduled)

        val withGap = build(listOf(h), lastSix.filter { it.date != daysAgo(3).toString() }).week
        assertEquals(5, withGap.completed)
        assertEquals(6, withGap.scheduled)
        assertEquals(83, withGap.percent)
    }

    @Test
    fun `partial progress is shown but does not count as done`() {
        val h = habit(1, target = 8f)
        val result = build(listOf(h), listOf(log(1, daysAgo(1), 5f), log(1, today, 3f)))
        val yesterday = result.week.days[5]
        val todayDay = result.week.days[6]
        assertEquals(HabitDayStatus.PARTIAL, yesterday.statuses[0])
        assertEquals(0, yesterday.completedCount)
        assertEquals(CalendarDayKind.PARTIAL, yesterday.kind)
        assertEquals(5f, yesterday.values[0], 0.0001f)
        assertEquals(HabitDayStatus.IN_PROGRESS, todayDay.statuses[0])
        assertEquals(0, result.week.completed)
    }

    @Test
    fun `habit done on a rest day counts like in the calendar`() {
        val h = habit(1, days = listOf(1, 3, 5)) // lunes, miercoles y viernes
        val tuesday = LocalDate.of(2026, 9, 22)
        val result = build(listOf(h), listOf(log(1, tuesday)))
        val day = result.month.days.first { it.date == tuesday }
        assertEquals(1, day.scheduledCount)
        assertEquals(1, day.completedCount)
        assertEquals(CalendarDayKind.PERFECT, day.kind)
        assertEquals(HabitDayStatus.NOT_SCHEDULED, result.month.days.first { it.date == LocalDate.of(2026, 9, 24) }.statuses[0])
    }

    @Test
    fun `days before the habit was created do not count`() {
        val h = habit(1, createdOn = daysAgo(2))
        val week = build(listOf(h), emptyList()).week
        assertEquals(0, week.completed)
        assertEquals(2, week.scheduled) // hace 2 dias y ayer; hoy sigue abierto
        assertEquals(HabitDayStatus.NOT_CREATED, week.days[3].statuses[0])
        assertEquals(HabitDayStatus.MISSED, week.days[4].statuses[0])
        assertEquals(HabitDayStatus.PENDING, week.days[6].statuses[0])
    }

    @Test
    fun `days match the calendar month calculator`() {
        val habits = listOf(
            habit(1),
            habit(2, days = listOf(1, 3, 5), target = 3f),
            habit(3, days = listOf(6, 7), createdOn = LocalDate.of(2026, 9, 10))
        )
        val logs = mutableListOf<HabitLog>()
        for (d in 1..29) {
            val date = LocalDate.of(2026, 9, d)
            if (d % 3 != 0) logs += log(1, date)
            if (d % 4 == 0) logs += log(2, date, if (d % 8 == 0) 3f else 1f)
            if (d % 5 == 0) logs += log(3, date)
        }
        val calendar = CalendarMonthCalculator.build(habits, logs, YearMonth.of(2026, 9), today, zone)
        val progress = build(habits, logs).constancy
        for (calendarDay in calendar.days) {
            val day = progress.days.first { it.date == calendarDay.date }
            assertEquals("tipo del ${calendarDay.date}", calendarDay.kind, day.kind)
            assertEquals("programados del ${calendarDay.date}", calendarDay.scheduledCount, day.scheduledCount)
            assertEquals("cumplidos del ${calendarDay.date}", calendarDay.completedCount, day.completedCount)
        }
    }

    @Test
    fun `streaks match DateUtils when today is the real date`() {
        val now = LocalDate.now()
        val daily = habit(1, createdOn = now.minusDays(40))
        val weekdays = habit(2, days = listOf(1, 2, 3, 4, 5), createdOn = now.minusDays(40))
        val logs = (1L..30L).filter { it != 12L }.map { log(1, now.minusDays(it)) } +
            (0L..20L).map { now.minusDays(it) }.filter { it.dayOfWeek.value <= 5 && it.dayOfMonth % 7 != 0 }.map { log(2, it) }
        val result = build(listOf(daily, weekdays), logs, on = now)
        for ((index, h) in listOf(daily, weekdays).withIndex()) {
            val dates = logs.filter { it.habitId == h.id }.map { it.date }.toSet()
            val (current, best) = DateUtils.calculateStreak(dates, h.frequencyDays)
            assertEquals(current, result.streaks[index].current)
            assertEquals(best, result.streaks[index].best)
        }
    }

    @Test
    fun `week delta is measured in points against the previous week`() {
        val h = habit(1)
        val previousHalf = (7L..13L).filter { it % 2 == 0L }.map { log(1, daysAgo(it)) } // 3 de 7
        val thisWeek = (1L..6L).map { log(1, daysAgo(it)) } + log(1, today) // 7 de 7
        val result = build(listOf(h), previousHalf + thisWeek)
        assertEquals(100, result.week.percent)
        assertEquals(100 - 43, result.week.deltaPoints)
        assertNull(build(listOf(habit(9, createdOn = daysAgo(3))), emptyList()).week.deltaPoints)
    }

    @Test
    fun `year covers at most the last twelve months`() {
        val h = habit(1, createdOn = LocalDate.of(2024, 5, 1))
        val year = build(listOf(h), listOf(log(1, today))).year
        assertEquals(LocalDate.of(2025, 10, 1), year.start)
        assertEquals(12, year.months.size)
        assertTrue(year.months.last().isCurrent)
        assertTrue(year.days.isEmpty())
    }

    @Test
    fun `near milestones explain what is missing`() {
        val daily = habit(1, title = "Leer")
        val done = habit(2, title = "Meditar")
        val mwf = habit(3, days = listOf(1, 3, 5), title = "Entrenamiento")
        val logs = listOf(log(1, daysAgo(1)), log(1, daysAgo(2))) + // Leer: 2 seguidos, hoy pendiente
            (0L..11L).map { log(2, daysAgo(it)) } + // Meditar: 12 seguidos con hoy
            listOf(log(3, LocalDate.of(2026, 9, 28))) // Entrenamiento: lunes
        val near = build(listOf(daily, done, mwf), logs).nearMilestones
        assertEquals(listOf(1L, 2L, 3L), near.map { it.habitId })
        assertEquals("Complétalo hoy y llegas a 3", near[0].hint)
        assertEquals(50, near[0].xpBonus)
        assertEquals(0, near[0].daysAway)
        assertEquals("Faltan 2 días", near[1].hint)
        assertEquals(14, near[1].target)
        assertEquals(175, near[1].xpBonus)
        assertEquals("Faltan 2 sesiones (mié y vie)", near[2].hint)
        assertEquals(3, near[2].daysAway)
    }

    @Test
    fun `near milestone hints for single and pending sessions`() {
        val mwf = habit(1, days = listOf(1, 3, 5))
        val weekdays = habit(2, days = listOf(1, 2, 3, 4, 5))
        val logs = listOf(log(1, LocalDate.of(2026, 9, 25)), log(1, LocalDate.of(2026, 9, 28)))
        val near = build(listOf(mwf, weekdays), logs).nearMilestones
        assertEquals("Falta 1 sesión (mié)", near.first { it.habitId == 1L }.hint)
        assertEquals("Faltan 3 sesiones", near.first { it.habitId == 2L }.hint)
    }

    @Test
    fun `constancy grid starts on monday and labels the months`() {
        val c = build(listOf(habit(1)), emptyList()).constancy
        assertEquals(LocalDate.of(2026, 6, 15), c.start)
        assertEquals(16 * 7, c.days.size)
        assertEquals(LocalDate.of(2026, 10, 4), c.days.last().date)
        assertEquals(
            listOf("jun", "", "jul", "", "", "", "ago", "", "", "", "", "sep", "", "", "", "oct"),
            c.monthLabels
        )
        assertEquals(CalendarDayKind.FUTURE, c.days.last().kind)
    }

    @Test
    fun `perfect streak skips free days and keeps today open`() {
        val h = habit(1, days = listOf(1, 2, 3, 4, 5, 6)) // domingo libre
        val logs = listOf(24, 25, 26, 28).map { log(1, LocalDate.of(2026, 9, it)) }
        assertEquals(4, build(listOf(h), logs).constancy.perfectStreak)
        assertEquals(5, build(listOf(h), logs + log(1, today)).constancy.perfectStreak)
        assertEquals(0, build(listOf(h), listOf(25, 26).map { log(1, LocalDate.of(2026, 9, it)) }).constancy.perfectStreak) // hueco el 28
    }

    @Test
    fun `weekday breakdown finds best and worst or none when even`() {
        val h = habit(1)
        val uneven = (1L..55L).map { daysAgo(it) }.filter { it.dayOfWeek.value != 6 }.map { log(1, it) }
        val wd = build(listOf(h), uneven).constancy.weekdays
        assertEquals(listOf(6), wd.worst)
        assertEquals(0, wd.days[5].percent)
        assertEquals(6, wd.best.size)

        val even = (1L..55L).map { log(1, daysAgo(it)) }
        val flat = build(listOf(h), even).constancy.weekdays
        assertTrue(flat.best.isEmpty())
        assertTrue(flat.worst.isEmpty())
    }

    @Test
    fun `insights name the habit that needs attention`() {
        val good = habit(1, title = "Meditar")
        val weak = habit(2, days = listOf(1, 3, 5), title = "Entrenamiento")
        val logs = (0L..40L).map { log(1, daysAgo(it)) } + listOf(log(2, LocalDate.of(2026, 9, 28)))
        val insights = build(listOf(good, weak), logs).insights
        val attention = insights.first { it.kind == InsightKind.NEEDS_ATTENTION }
        assertEquals(2L, attention.habitId)
        assertTrue(attention.text.startsWith("Entrenamiento va por debajo: 1 de 3 esta semana"))
    }

    @Test
    fun `insights without data ask to keep going`() {
        val insights = build(listOf(habit(1, createdOn = today)), emptyList()).insights
        assertEquals(listOf(InsightKind.NOT_ENOUGH_DATA), insights.map { it.kind })
    }

    @Test
    fun `achievements use real streaks and keep archived history`() {
        val active = habit(1, target = 8f, title = "Agua")
        val archived = habit(2, title = "Correr").copy(isArchived = true)
        val logs = (1L..20L).map { log(1, daysAgo(it), 5f) } + // solo parciales: no hay racha
            (10L..27L).map { log(2, daysAgo(it)) } // 18 seguidos en un habito archivado
        val stats = UserStats(xp = 10725, totalFocusMinutes = 1240)
        val a = build(listOf(active), logs, stats, archived = listOf(archived)).achievements
        assertEquals(18, a.totalCompleted)
        assertEquals(18, a.bestStreak)
        assertEquals("Correr", a.bestStreakHabitTitle)
        assertEquals(15, a.level.currentLevel)
        assertEquals(11, a.habitsToNextLevel)
        val streak30 = a.badges.first { it.badge.id == "streak_30" }
        assertFalse(streak30.unlocked)
        assertEquals(18, streak30.current)
        assertEquals("streak_30", a.badges.first().badge.id)
        assertEquals(0, a.medals.first().earned.size)
        assertEquals(3, a.medals.first().next)
        assertEquals(50, a.medals.first().nextXp)
    }

    @Test
    fun `badges already stored stay unlocked`() {
        val stats = UserStats(unlockedBadgeIds = listOf("streak_30"))
        val a = build(listOf(habit(1)), emptyList(), stats).achievements
        assertTrue(a.badges.first { it.badge.id == "streak_30" }.unlocked)
    }

    @Test
    fun `hardcore mode needs fewer habits to level up`() {
        val stats = UserStats(xp = 10725, isHardcoreMode = true)
        assertEquals(9, build(listOf(habit(1)), emptyList(), stats).achievements.habitsToNextLevel) // 275 / 31
    }

    @Test
    fun `hardcore mode shows the milestone bonus the repository pays`() {
        val logs = listOf(log(1, daysAgo(2)), log(1, daysAgo(1)))
        val normal = build(listOf(habit(1)), logs)
        assertEquals(50, normal.nearMilestones.first().xpBonus)
        assertFalse(normal.achievements.isHardcore)

        val hard = build(listOf(habit(1)), logs, UserStats(isHardcoreMode = true))
        assertEquals(62, hard.nearMilestones.first().xpBonus) // (50 * 1.25).toInt()
        assertEquals(62, hard.achievements.medals.first().nextXp)
        assertTrue(hard.achievements.isHardcore)
    }

    @Test
    fun `no habits does not crash`() {
        val result = build(emptyList(), emptyList())
        assertEquals(0, result.week.scheduled)
        assertEquals(0, result.week.percent)
        assertTrue(result.week.habits.isEmpty())
        assertTrue(result.nearMilestones.isEmpty())
        assertNull(result.achievements.firstDay)
        assertEquals(CalendarDayKind.FREE, result.constancy.days.first().kind)
    }
}
