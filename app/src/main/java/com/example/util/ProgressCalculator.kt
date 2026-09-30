package com.example.util

import com.example.model.AllBadges
import com.example.model.Badge
import com.example.model.GamificationConfig
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.StreakMilestones
import com.example.model.UserStats
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Estado de un habito en un dia. Misma regla que CalendarMonthCalculator:
 * un habito cuenta si le toca por frecuencia desde su creacion, o si se hizo aunque no tocara.
 */
enum class HabitDayStatus {
    /** Llego a la meta (value >= targetValue). */
    DONE,

    /** Dia pasado con avance que no llego a la meta. No cuenta como cumplido. */
    PARTIAL,

    /** Hoy, con avance que todavia no llega a la meta. */
    IN_PROGRESS,

    /** Hoy, programado y sin avance. No cuenta como fallo mientras el dia siga abierto. */
    PENDING,

    /** Dia pasado, programado y sin avance. */
    MISSED,

    /** Dia futuro programado. */
    UPCOMING,

    /** No le tocaba ese dia y no se hizo. */
    NOT_SCHEDULED,

    /** Dia anterior a la creacion del habito. */
    NOT_CREATED;

    /** true si el habito entra en el conteo del dia (el mismo que dibuja el Calendario). */
    val countsForDay: Boolean
        get() = this == DONE || this == PARTIAL || this == IN_PROGRESS ||
            this == PENDING || this == MISSED || this == UPCOMING
}

/** Un dia visto por Progreso. [kind] usa las mismas reglas que el Calendario. */
data class ProgressDay(
    val date: LocalDate,
    val scheduledCount: Int,
    val completedCount: Int,
    val kind: CalendarDayKind,
    val isToday: Boolean,
    /** Estado de cada habito, en el orden de ProgressSummary.habits. */
    val statuses: List<HabitDayStatus>,
    /** Valor registrado de cada habito (0 sin registro), mismo orden. */
    val values: List<Float>
) {
    val ratio: Float get() = if (scheduledCount == 0) 0f else completedCount.toFloat() / scheduledCount
}

enum class ProgressPeriod { WEEK, MONTH, YEAR }

data class HabitPeriodStat(
    val habitId: Long,
    val completed: Int,
    val scheduled: Int,
    val percent: Int,
    val currentStreak: Int
)

data class MonthStat(
    val month: YearMonth,
    val completed: Int,
    val scheduled: Int,
    val percent: Int,
    val isCurrent: Boolean
)

data class PeriodSummary(
    val period: ProgressPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val completed: Int,
    val scheduled: Int,
    val percent: Int,
    /** Diferencia en puntos contra el periodo anterior del mismo largo. Null si no hay con que comparar. */
    val deltaPoints: Int?,
    val perfectDays: Int,
    /** Habitos con 80% o mas en el periodo. */
    val strongHabits: Int,
    /** De mejor a peor porcentaje. Solo habitos que tuvieron algo programado en el periodo. */
    val habits: List<HabitPeriodStat>,
    /** Un elemento por dia (semana y mes). Vacio en ano. */
    val days: List<ProgressDay>,
    /** Un elemento por mes (ano). Vacio en semana y mes. */
    val months: List<MonthStat>
)

data class HabitStreak(val habitId: Long, val current: Int, val best: Int) {
    val isRecord: Boolean get() = current > 0 && current == best
}

/** Siguiente hito de racha de un habito (3, 7, 14, 21, 30, 50...). */
data class NearMilestone(
    val habitId: Long,
    val currentStreak: Int,
    val target: Int,
    val remaining: Int,
    /** XP extra al llegar, con el multiplicador de Hardcore si esta activo (igual que el repositorio). */
    val xpBonus: Int,
    /** Texto listo para mostrar: "Complétalo hoy y llegas a 3", "Faltan 2 días"... */
    val hint: String,
    /** Dias de calendario hasta poder alcanzar el hito. */
    val daysAway: Int
)

enum class InsightKind { BEST_WEEKDAY, EVEN_WEEK, NEEDS_ATTENTION, BEST_MONTH, NOT_ENOUGH_DATA }

data class ProgressInsight(val kind: InsightKind, val text: String, val habitId: Long? = null)

data class WeekdayStat(val dayOfWeek: Int, val completed: Int, val scheduled: Int) {
    val percent: Int? get() = if (scheduled == 0) null else (completed * 100f / scheduled).roundToInt()
}

/** Cumplimiento por dia de la semana. [best] y [worst] vacios si todos quedan parejos. */
data class WeekdayBreakdown(val days: List<WeekdayStat>, val best: List<Int>, val worst: List<Int>)

data class HabitConstancy(
    val habitId: Long,
    val completed: Int,
    val scheduled: Int,
    val percent: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val weekdays: WeekdayBreakdown
)

data class ConstancySummary(
    val weeks: Int,
    /** Lunes de la primera columna. */
    val start: LocalDate,
    /** weeks * 7 dias, de lunes a domingo, columna por columna. */
    val days: List<ProgressDay>,
    /** Una etiqueta por columna ("jun", "" ...). */
    val monthLabels: List<String>,
    val completed: Int,
    val scheduled: Int,
    val percent: Int,
    val perfectDays: Int,
    /** Dias perfectos seguidos que terminan hoy, igual que el Calendario. */
    val perfectStreak: Int,
    val weekdays: WeekdayBreakdown,
    /** Mismo orden que ProgressSummary.habits. */
    val habits: List<HabitConstancy>
)

data class HabitMedals(
    val habitId: Long,
    val currentStreak: Int,
    val bestStreak: Int,
    /** Hitos ya alcanzados alguna vez (se conservan aunque la racha se rompa). */
    val earned: List<Int>,
    val next: Int?,
    /** XP extra de [next], con el multiplicador de Hardcore si esta activo. */
    val nextXp: Int
)

data class BadgeProgress(val badge: Badge, val unlocked: Boolean, val current: Int, val required: Int) {
    val fraction: Float get() = if (unlocked || required <= 0) 1f else (current.toFloat() / required).coerceIn(0f, 1f)
}

data class AchievementsSummary(
    val level: GamificationConfig.LevelProgress,
    /** Habitos cumplidos que faltan, aproximados, para el siguiente nivel. */
    val habitsToNextLevel: Int,
    /** Mismo orden que ProgressSummary.habits. */
    val medals: List<HabitMedals>,
    /** Pendientes primero (las mas avanzadas antes), despues las ganadas en su orden original. */
    val badges: List<BadgeProgress>,
    val unlockedBadges: Int,
    /** Metas cumplidas en total, contando habitos archivados. */
    val totalCompleted: Int,
    val bestStreak: Int,
    val bestStreakHabitId: Long?,
    val bestStreakHabitTitle: String?,
    val focusMinutes: Int,
    val perfectDaysTotal: Int,
    val firstDay: LocalDate?,
    val isHardcore: Boolean
)

data class ProgressSummary(
    val today: LocalDate,
    /** Habitos activos en el orden de Hoy. Los estados de cada dia siguen este orden. */
    val habits: List<Habit>,
    /** Primer dia con datos: creacion del habito mas antiguo o primer registro. */
    val firstDay: LocalDate,
    val week: PeriodSummary,
    val month: PeriodSummary,
    val year: PeriodSummary,
    /** Mismo orden que [habits]. */
    val streaks: List<HabitStreak>,
    /** Mas cercanas primero. */
    val nearMilestones: List<NearMilestone>,
    val insights: List<ProgressInsight>,
    val constancy: ConstancySummary,
    val achievements: AchievementsSummary
) {
    fun period(period: ProgressPeriod): PeriodSummary = when (period) {
        ProgressPeriod.WEEK -> week
        ProgressPeriod.MONTH -> month
        ProgressPeriod.YEAR -> year
    }

    fun habit(habitId: Long): Habit? = habits.firstOrNull { it.id == habitId }

    fun streakOf(habitId: Long): HabitStreak? = streaks.firstOrNull { it.habitId == habitId }
}

/**
 * Calculo puro de la pestana Progreso (Resumen, Constancia y Logros).
 * No toca Android ni la base de datos: se prueba con JUnit simple.
 *
 * Reglas:
 * - Programado: le toca por frecuencia (lista vacia = todos los dias) y el dia no es anterior a su creacion.
 * - Cumplido: value >= targetValue. Si se cumplio un dia que no tocaba, tambien cuenta (como en el Calendario).
 * - Porcentajes: cumplidos / programados. Hoy solo cuenta lo ya cumplido; lo pendiente se cuenta cuando el dia termina.
 * - Racha de un habito: misma regla que DateUtils.calculateStreak, con [today] explicito.
 */
object ProgressCalculator {

    const val CONSTANCY_WEEKS = 16
    const val WEEKDAY_WEEKS = 8
    /** Un dia de la semana entra en la comparacion si tuvo al menos estos habitos programados. */
    const val WEEKDAY_MIN_SCHEDULED = 3
    const val STRONG_PERCENT = 80
    const val WEAK_PERCENT = 50
    private const val MAX_LOOKBACK_DAYS = 730L
    private const val MAX_YEAR_MONTHS = 12L

    private val MONTH_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val MONTH_FULL = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )
    private val WEEKDAY_SHORT = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    private val WEEKDAY_PLURAL = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos")

    /** Nombre corto del mes en espanol, sin punto: "ene" ... "dic". */
    fun monthShort(month: Int): String = MONTH_SHORT[month - 1]

    /** Nombre completo del mes en espanol: "enero" ... "diciembre". */
    fun monthFull(month: Int): String = MONTH_FULL[month - 1]

    /** Plural del dia de la semana (1 = lunes): "lunes", "sábados"... */
    fun weekdayPlural(dayOfWeek: Int): String = WEEKDAY_PLURAL[dayOfWeek - 1]

    fun build(
        habits: List<Habit>,
        archivedHabits: List<Habit>,
        logs: List<HabitLog>,
        stats: UserStats,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): ProgressSummary {
        val valuesByHabit: Map<Long, Map<LocalDate, Float>> = logs
            .groupBy { it.habitId }
            .mapValues { (_, habitLogs) ->
                habitLogs.mapNotNull { log ->
                    runCatching { LocalDate.parse(log.date) }.getOrNull()?.let { it to log.value }
                }.toMap()
            }

        fun trackOf(habit: Habit) = Track(
            habit = habit,
            values = valuesByHabit[habit.id] ?: emptyMap(),
            createdDay = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
        )

        val tracks = habits.map(::trackOf)
        val activeIds = habits.map { it.id }.toSet()
        val archivedTracks = archivedHabits.filter { it.id !in activeIds }.map(::trackOf)

        val firstLogDay = tracks.flatMap { it.values.keys }.minOrNull()
        val firstDay = listOfNotNull(tracks.minOfOrNull { it.createdDay }, firstLogDay).minOrNull()
            ?.coerceAtMost(today) ?: today

        val dayCache = HashMap<LocalDate, ProgressDay>()
        fun dayOf(date: LocalDate): ProgressDay = dayCache.getOrPut(date) { evaluateDay(date, tracks, today) }

        val streaks = tracks.map { track ->
            val (current, best) = streakOf(track.doneDates, track.habit.frequencyDays, today)
            HabitStreak(track.habit.id, current, best)
        }

        val week = period(
            period = ProgressPeriod.WEEK,
            start = today.minusDays(6), end = today,
            previousStart = today.minusDays(13), previousEnd = today.minusDays(7),
            tracks = tracks, streaks = streaks, today = today, dayOf = ::dayOf, withDays = true
        )
        val month = period(
            period = ProgressPeriod.MONTH,
            start = today.minusDays(29), end = today,
            previousStart = today.minusDays(59), previousEnd = today.minusDays(30),
            tracks = tracks, streaks = streaks, today = today, dayOf = ::dayOf, withDays = true
        )
        val yearStart = maxOf(firstDay, YearMonth.from(today).minusMonths(MAX_YEAR_MONTHS - 1).atDay(1))
        val year = period(
            period = ProgressPeriod.YEAR,
            start = yearStart, end = today,
            previousStart = null, previousEnd = null,
            tracks = tracks, streaks = streaks, today = today, dayOf = ::dayOf, withDays = false
        ).let { summary ->
            val months = mutableListOf<MonthStat>()
            var ym = YearMonth.from(yearStart)
            while (!ym.isAfter(YearMonth.from(today))) {
                val from = maxOf(ym.atDay(1), yearStart)
                val to = minOf(ym.atEndOfMonth(), today)
                val (done, scheduled) = countRange(from, to, null, today, ::dayOf)
                months += MonthStat(ym, done, scheduled, percentOf(done, scheduled), ym == YearMonth.from(today))
                ym = ym.plusMonths(1)
            }
            summary.copy(months = months)
        }

        val constancy = constancy(tracks, streaks, firstDay, today, ::dayOf)
        val near = nearMilestones(tracks, streaks, today, stats.isHardcoreMode)
        val insights = insights(habits, week, month, year, constancy)
        val achievements = achievements(tracks, archivedTracks, streaks, stats, firstDay, today, ::dayOf)

        return ProgressSummary(
            today = today,
            habits = habits,
            firstDay = firstDay,
            week = week,
            month = month,
            year = year,
            streaks = streaks,
            nearMilestones = near,
            insights = insights,
            constancy = constancy,
            achievements = achievements
        )
    }

    // ------------------------------------------------------------------ reglas por habito

    private class Track(val habit: Habit, val values: Map<LocalDate, Float>, val createdDay: LocalDate) {
        private val target = if (habit.targetValue > 0f) habit.targetValue else 1f

        val doneDates: Set<LocalDate> by lazy { values.filterValues { it >= target }.keys }

        fun value(date: LocalDate): Float = values[date] ?: 0f

        fun isDone(date: LocalDate): Boolean = value(date) >= target

        fun isProgrammed(date: LocalDate): Boolean =
            !date.isBefore(createdDay) &&
                (habit.frequencyDays.isEmpty() || date.dayOfWeek.value in habit.frequencyDays)

        fun status(date: LocalDate, today: LocalDate): HabitDayStatus {
            if (isDone(date)) return HabitDayStatus.DONE
            if (!isProgrammed(date)) {
                return if (date.isBefore(createdDay)) HabitDayStatus.NOT_CREATED else HabitDayStatus.NOT_SCHEDULED
            }
            val hasProgress = value(date) > 0f
            return when {
                date.isAfter(today) -> HabitDayStatus.UPCOMING
                date == today -> if (hasProgress) HabitDayStatus.IN_PROGRESS else HabitDayStatus.PENDING
                hasProgress -> HabitDayStatus.PARTIAL
                else -> HabitDayStatus.MISSED
            }
        }
    }

    private fun evaluateDay(date: LocalDate, tracks: List<Track>, today: LocalDate): ProgressDay {
        val statuses = tracks.map { it.status(date, today) }
        val scheduled = statuses.count { it.countsForDay }
        val completed = statuses.count { it == HabitDayStatus.DONE }
        val hasProgress = statuses.any { it == HabitDayStatus.PARTIAL || it == HabitDayStatus.IN_PROGRESS }
        val isToday = date == today
        val kind = when {
            date.isAfter(today) -> CalendarDayKind.FUTURE
            scheduled == 0 -> CalendarDayKind.FREE
            completed == scheduled -> CalendarDayKind.PERFECT
            completed > 0 || hasProgress || isToday -> CalendarDayKind.PARTIAL
            else -> CalendarDayKind.MISSED
        }
        return ProgressDay(
            date = date,
            scheduledCount = scheduled,
            completedCount = completed,
            kind = kind,
            isToday = isToday,
            statuses = statuses,
            values = tracks.map { it.value(date) }
        )
    }

    /** Cumplidos y programados de un dia para los porcentajes. Hoy solo suma lo ya cumplido. */
    private fun countDay(day: ProgressDay, habitIndex: Int?): Pair<Int, Int> {
        if (day.kind == CalendarDayKind.FUTURE) return 0 to 0
        val statuses = if (habitIndex == null) day.statuses else listOf(day.statuses[habitIndex])
        val done = statuses.count { it == HabitDayStatus.DONE }
        val scheduled = if (day.isToday) done else statuses.count { it.countsForDay }
        return done to scheduled
    }

    private fun countRange(
        from: LocalDate,
        to: LocalDate,
        habitIndex: Int?,
        today: LocalDate,
        dayOf: (LocalDate) -> ProgressDay
    ): Pair<Int, Int> {
        var done = 0
        var scheduled = 0
        var date = from
        val last = minOf(to, today)
        while (!date.isAfter(last)) {
            val (d, s) = countDay(dayOf(date), habitIndex)
            done += d
            scheduled += s
            date = date.plusDays(1)
        }
        return done to scheduled
    }

    private fun percentOf(done: Int, scheduled: Int): Int =
        if (scheduled == 0) 0 else (done * 100f / scheduled).roundToInt()

    /** Igual que DateUtils.calculateStreak, con [today] explicito para poder probarlo. */
    internal fun streakOf(done: Set<LocalDate>, frequencyDays: List<Int>, today: LocalDate): Pair<Int, Int> {
        if (done.isEmpty()) return 0 to 0
        val scheduledDays = frequencyDays.filter { it in 1..7 }.toSet()
        val isDaily = scheduledDays.isEmpty() || scheduledDays.size == 7
        fun isScheduled(date: LocalDate): Boolean = isDaily || date.dayOfWeek.value in scheduledDays

        val earliest = done.minOrNull() ?: return 0 to 0
        var current = if (today in done) 1 else 0
        var day = today.minusDays(1)
        while (!day.isBefore(earliest)) {
            if (day in done) {
                current++
            } else if (isScheduled(day)) {
                break
            }
            day = day.minusDays(1)
        }

        val lastDay = done.maxOrNull()?.takeIf { it.isAfter(today) } ?: today
        var best = 0
        var run = 0
        var cursor = earliest
        while (!cursor.isAfter(lastDay)) {
            if (cursor in done) {
                run++
                if (run > best) best = run
            } else if (isScheduled(cursor) && cursor != today) {
                run = 0
            }
            cursor = cursor.plusDays(1)
        }
        return current to maxOf(current, best)
    }

    // ------------------------------------------------------------------ Resumen

    private fun period(
        period: ProgressPeriod,
        start: LocalDate,
        end: LocalDate,
        previousStart: LocalDate?,
        previousEnd: LocalDate?,
        tracks: List<Track>,
        streaks: List<HabitStreak>,
        today: LocalDate,
        dayOf: (LocalDate) -> ProgressDay,
        withDays: Boolean
    ): PeriodSummary {
        val (done, scheduled) = countRange(start, end, null, today, dayOf)
        val percent = percentOf(done, scheduled)
        val delta = if (previousStart != null && previousEnd != null) {
            val (prevDone, prevScheduled) = countRange(previousStart, previousEnd, null, today, dayOf)
            if (prevScheduled > 0 && scheduled > 0) percent - percentOf(prevDone, prevScheduled) else null
        } else {
            null
        }

        var perfect = 0
        val days = mutableListOf<ProgressDay>()
        var date = start
        while (!date.isAfter(end)) {
            val day = dayOf(date)
            if (day.kind == CalendarDayKind.PERFECT) perfect++
            if (withDays) days += day
            date = date.plusDays(1)
        }

        val rows = tracks.mapIndexedNotNull { index, track ->
            val (d, s) = countRange(start, end, index, today, dayOf)
            if (s == 0) null else HabitPeriodStat(track.habit.id, d, s, percentOf(d, s), streaks[index].current)
        }
        val order = tracks.map { it.habit.id }
        val sorted = rows.sortedWith(
            compareByDescending<HabitPeriodStat> { it.percent }
                .thenByDescending { it.scheduled }
                .thenBy { order.indexOf(it.habitId) }
        )

        return PeriodSummary(
            period = period,
            start = start,
            end = end,
            completed = done,
            scheduled = scheduled,
            percent = percent,
            deltaPoints = delta,
            perfectDays = perfect,
            strongHabits = sorted.count { it.percent >= STRONG_PERCENT },
            habits = sorted,
            days = days,
            months = emptyList()
        )
    }

    private fun nearMilestones(
        tracks: List<Track>,
        streaks: List<HabitStreak>,
        today: LocalDate,
        hardcore: Boolean
    ): List<NearMilestone> {
        val milestones = StreakMilestones.MILESTONE_DAYS.sorted()
        val result = tracks.mapIndexedNotNull { index, track ->
            val current = streaks[index].current
            val target = milestones.firstOrNull { it > current } ?: return@mapIndexedNotNull null
            val remaining = target - current
            val todayPending = track.isProgrammed(today) && !track.isDone(today)
            val sessions = upcomingSessions(track, today, remaining, includeToday = todayPending)
                ?: return@mapIndexedNotNull null
            val xp = milestoneXp(track.habit, target, hardcore)
            val days = track.habit.frequencyDays.filter { it in 1..7 }.toSet()
            val isDaily = days.isEmpty() || days.size == 7
            val hint = when {
                remaining == 1 && todayPending -> "Complétalo hoy y llegas a $target"
                isDaily && todayPending -> "Faltan $remaining días, contando hoy"
                isDaily -> if (remaining == 1) "Falta 1 día" else "Faltan $remaining días"
                else -> {
                    val names = sessions.map { if (it == today) "hoy" else WEEKDAY_SHORT[it.dayOfWeek.value - 1] }
                    when (remaining) {
                        1 -> "Falta 1 sesión (${names[0]})"
                        2 -> "Faltan 2 sesiones (${names[0]} y ${names[1]})"
                        else -> "Faltan $remaining sesiones"
                    }
                }
            }
            NearMilestone(
                habitId = track.habit.id,
                currentStreak = current,
                target = target,
                remaining = remaining,
                xpBonus = xp,
                hint = hint,
                daysAway = ChronoUnit.DAYS.between(today, sessions.last()).toInt()
            )
        }
        val order = tracks.map { it.habit.id }
        return result.sortedWith(
            compareBy<NearMilestone> { it.remaining }
                .thenBy { it.daysAway }
                .thenBy { order.indexOf(it.habitId) }
        )
    }

    /** Proximas [count] fechas en que le toca el habito. Null si no se encuentran en un ano. */
    private fun upcomingSessions(track: Track, today: LocalDate, count: Int, includeToday: Boolean): List<LocalDate>? {
        val result = mutableListOf<LocalDate>()
        var date = if (includeToday) today else today.plusDays(1)
        val limit = today.plusDays(400)
        while (result.size < count && !date.isAfter(limit)) {
            if (track.isProgrammed(date)) result += date
            date = date.plusDays(1)
        }
        return if (result.size == count && count > 0) result else null
    }

    private fun insights(
        habits: List<Habit>,
        week: PeriodSummary,
        month: PeriodSummary,
        year: PeriodSummary,
        constancy: ConstancySummary
    ): List<ProgressInsight> {
        val result = mutableListOf<ProgressInsight>()

        val weekdays = constancy.weekdays
        val measured = weekdays.days.filter { it.scheduled >= WEEKDAY_MIN_SCHEDULED }
        if (measured.size >= 2) {
            if (weekdays.best.isNotEmpty() && weekdays.worst.isNotEmpty()) {
                val best = weekdays.days.first { it.dayOfWeek == weekdays.best.first() }
                val worst = weekdays.days.first { it.dayOfWeek == weekdays.worst.first() }
                if ((best.percent ?: 0) - (worst.percent ?: 0) >= 10) {
                    result += ProgressInsight(
                        InsightKind.BEST_WEEKDAY,
                        "Los ${weekdayPlural(best.dayOfWeek)} son tu mejor día (${best.percent}%). " +
                            "Los ${weekdayPlural(worst.dayOfWeek)} cuestan más (${worst.percent}%)."
                    )
                } else {
                    result += ProgressInsight(
                        InsightKind.EVEN_WEEK,
                        "Cumples parejo toda la semana: entre ${worst.percent}% y ${best.percent}%."
                    )
                }
            } else {
                val percent = measured.first().percent ?: 0
                result += ProgressInsight(InsightKind.EVEN_WEEK, "Cumples parejo toda la semana: $percent% cada día.")
            }
        }

        val weak = week.habits.filter { it.scheduled >= 2 && it.percent < WEAK_PERCENT }.minByOrNull { it.percent }
        val weakHabit = weak?.let { row -> habits.firstOrNull { it.id == row.habitId } }
        if (weak != null && weakHabit != null) {
            val monthPercent = month.habits.firstOrNull { it.habitId == weak.habitId }?.percent ?: weak.percent
            result += ProgressInsight(
                InsightKind.NEEDS_ATTENTION,
                "${weakHabit.title} va por debajo: ${weak.completed} de ${weak.scheduled} esta semana y " +
                    "$monthPercent% en los últimos 30 días. Prueba otro día u otra meta.",
                weakHabit.id
            )
        }

        val months = year.months.filter { it.scheduled > 0 }
        if (months.size >= 2) {
            val bestPercent = months.maxOf { it.percent }
            val best = months.last { it.percent == bestPercent }
            val current = months.last()
            if (best == current) {
                val first = months.first()
                result += ProgressInsight(
                    InsightKind.BEST_MONTH,
                    "${monthFull(current.month.monthValue).replaceFirstChar { it.uppercase() }} va siendo tu mejor mes: " +
                        "${current.percent}%. En ${monthFull(first.month.monthValue)} fue ${first.percent}%."
                )
            } else {
                result += ProgressInsight(
                    InsightKind.BEST_MONTH,
                    "Tu mejor mes fue ${monthFull(best.month.monthValue)} (${best.percent}%). Este mes vas en ${current.percent}%."
                )
            }
        }

        if (result.isEmpty()) {
            result += ProgressInsight(
                InsightKind.NOT_ENOUGH_DATA,
                "Todavía no hay suficientes días para comparar. Registra tus hábitos unos días y aquí aparecerán tus patrones."
            )
        }
        return result
    }

    // ------------------------------------------------------------------ Constancia

    private fun constancy(
        tracks: List<Track>,
        streaks: List<HabitStreak>,
        firstDay: LocalDate,
        today: LocalDate,
        dayOf: (LocalDate) -> ProgressDay
    ): ConstancySummary {
        val start = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(CONSTANCY_WEEKS - 1L)
        val days = (0 until CONSTANCY_WEEKS * 7).map { dayOf(start.plusDays(it.toLong())) }

        val labels = MutableList(CONSTANCY_WEEKS) { "" }
        for (w in 0 until CONSTANCY_WEEKS) {
            val monday = start.plusWeeks(w.toLong())
            val firstOfMonth = (0L..6L).map { monday.plusDays(it) }.firstOrNull { it.dayOfMonth == 1 }
            if (firstOfMonth != null) labels[w] = monthShort(firstOfMonth.monthValue)
        }
        if (labels[0].isEmpty()) labels[0] = monthShort(start.monthValue)
        if (labels.size > 1 && labels[1].isNotEmpty()) labels[0] = ""

        val (done, scheduled) = countRange(start, today, null, today, dayOf)
        val perfect = days.count { !it.date.isAfter(today) && it.kind == CalendarDayKind.PERFECT }

        var perfectStreak = 0
        val limit = maxOf(firstDay, today.minusDays(MAX_LOOKBACK_DAYS))
        var date = today
        while (!date.isBefore(limit)) {
            val day = dayOf(date)
            when (day.kind) {
                CalendarDayKind.PERFECT -> perfectStreak++
                CalendarDayKind.FREE -> Unit
                else -> if (date != today) break
            }
            date = date.minusDays(1)
        }

        val weekStart = today.minusDays(WEEKDAY_WEEKS * 7L - 1)
        val habitStats = tracks.mapIndexed { index, track ->
            val (d, s) = countRange(start, today, index, today, dayOf)
            HabitConstancy(
                habitId = track.habit.id,
                completed = d,
                scheduled = s,
                percent = percentOf(d, s),
                currentStreak = streaks[index].current,
                bestStreak = streaks[index].best,
                weekdays = weekdays(weekStart, today, index, dayOf)
            )
        }

        return ConstancySummary(
            weeks = CONSTANCY_WEEKS,
            start = start,
            days = days,
            monthLabels = labels,
            completed = done,
            scheduled = scheduled,
            percent = percentOf(done, scheduled),
            perfectDays = perfect,
            perfectStreak = perfectStreak,
            weekdays = weekdays(weekStart, today, null, dayOf),
            habits = habitStats
        )
    }

    private fun weekdays(
        from: LocalDate,
        today: LocalDate,
        habitIndex: Int?,
        dayOf: (LocalDate) -> ProgressDay
    ): WeekdayBreakdown {
        val done = IntArray(7)
        val scheduled = IntArray(7)
        var date = from
        while (!date.isAfter(today)) {
            val (d, s) = countDay(dayOf(date), habitIndex)
            done[date.dayOfWeek.value - 1] += d
            scheduled[date.dayOfWeek.value - 1] += s
            date = date.plusDays(1)
        }
        val stats = (1..7).map { WeekdayStat(it, done[it - 1], scheduled[it - 1]) }
        val candidates = stats.filter { it.scheduled >= WEEKDAY_MIN_SCHEDULED }
        val max = candidates.maxOfOrNull { it.percent ?: 0 }
        val min = candidates.minOfOrNull { it.percent ?: 0 }
        return if (max == null || min == null || max == min) {
            WeekdayBreakdown(stats, emptyList(), emptyList())
        } else {
            WeekdayBreakdown(
                days = stats,
                best = candidates.filter { it.percent == max }.map { it.dayOfWeek },
                worst = candidates.filter { it.percent == min }.map { it.dayOfWeek }
            )
        }
    }

    // ------------------------------------------------------------------ Logros

    private fun achievements(
        tracks: List<Track>,
        archivedTracks: List<Track>,
        streaks: List<HabitStreak>,
        stats: UserStats,
        firstDay: LocalDate,
        today: LocalDate,
        dayOf: (LocalDate) -> ProgressDay
    ): AchievementsSummary {
        val level = GamificationConfig.getProgress(stats.xp)
        val xpPerHabit = if (stats.isHardcoreMode) {
            (GamificationConfig.XP_HABIT_COMPLETION * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt()
        } else {
            GamificationConfig.XP_HABIT_COMPLETION
        }
        val habitsToNextLevel = ceil(level.xpNeededForNextLevel.toDouble() / xpPerHabit).toInt()

        val milestones = StreakMilestones.MILESTONE_DAYS.sorted()
        val medals = tracks.mapIndexed { index, track ->
            val best = streaks[index].best
            val next = milestones.firstOrNull { it > best }
            HabitMedals(
                habitId = track.habit.id,
                currentStreak = streaks[index].current,
                bestStreak = best,
                earned = milestones.filter { it <= best },
                next = next,
                nextXp = next?.let { milestoneXp(track.habit, it, stats.isHardcoreMode) } ?: 0
            )
        }

        val allTracks = tracks + archivedTracks
        val totalCompleted = allTracks.sumOf { it.doneDates.size }
        val bestPairs = allTracks.map { it to streakOf(it.doneDates, it.habit.frequencyDays, today).second }
        val bestStreak = bestPairs.maxOfOrNull { it.second } ?: 0
        val bestTrack = bestPairs.firstOrNull { it.second == bestStreak && bestStreak > 0 }?.first

        val badges = AllBadges.map { badge ->
            val (current, required) = when {
                badge.requiredCompletions > 0 -> totalCompleted to badge.requiredCompletions
                badge.requiredStreak > 0 -> bestStreak to badge.requiredStreak
                badge.requiredFocusMinutes > 0 -> stats.totalFocusMinutes to badge.requiredFocusMinutes
                badge.requiredXp > 0 -> stats.xp to badge.requiredXp
                else -> 0 to 0
            }
            BadgeProgress(
                badge = badge,
                unlocked = badge.id in stats.unlockedBadgeIds || (required > 0 && current >= required),
                current = current,
                required = required
            )
        }
        val ordered = badges.filter { !it.unlocked }.sortedByDescending { it.fraction } + badges.filter { it.unlocked }

        var perfectTotal = 0
        var date = firstDay
        while (!date.isAfter(today)) {
            if (dayOf(date).kind == CalendarDayKind.PERFECT) perfectTotal++
            date = date.plusDays(1)
        }

        return AchievementsSummary(
            level = level,
            habitsToNextLevel = habitsToNextLevel,
            medals = medals,
            badges = ordered,
            unlockedBadges = badges.count { it.unlocked },
            totalCompleted = totalCompleted,
            bestStreak = bestStreak,
            bestStreakHabitId = bestTrack?.habit?.id,
            bestStreakHabitTitle = bestTrack?.habit?.title,
            focusMinutes = stats.totalFocusMinutes,
            perfectDaysTotal = perfectTotal,
            firstDay = if (tracks.isEmpty()) null else firstDay,
            isHardcore = stats.isHardcoreMode
        )
    }

    /** Bono de un hito como lo paga HabitRepository.checkStreakMilestone: x1.25 en Hardcore. */
    private fun milestoneXp(habit: Habit, days: Int, hardcore: Boolean): Int {
        val base = StreakMilestones.getMilestone(habit.id, habit.title, days)?.xpBonus ?: 0
        return if (hardcore) (base * GamificationConfig.HARDCORE_XP_MULTIPLIER).toInt() else base
    }
}
