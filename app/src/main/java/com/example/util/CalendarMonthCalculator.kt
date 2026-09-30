package com.example.util

import com.example.model.Habit
import com.example.model.HabitLog
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/** Como se pinta un dia en el calendario mensual. */
enum class CalendarDayKind {
    /** Todos los habitos programados llegaron a su meta: disco verde lleno. */
    PERFECT,

    /** Hay avance, o es hoy y el dia sigue abierto: anillo parcial. */
    PARTIAL,

    /** Habia habitos programados y ninguno tuvo avance: solo el anillo gris. */
    MISSED,

    /** Ningun habito tocaba ese dia: sin anillo. No rompe la racha. */
    FREE,

    /** Dia posterior a hoy: numero atenuado, no se puede marcar. */
    FUTURE
}

data class CalendarDaySummary(
    val date: LocalDate,
    /** Habitos que cuentan ese dia: programados, o hechos aunque no tocaran. */
    val scheduledCount: Int,
    /** Habitos cuyo valor llego a la meta (value >= targetValue). */
    val completedCount: Int,
    val kind: CalendarDayKind,
    val isToday: Boolean,
    /** Ids de los habitos que cuentan ese dia, para armar la lista del dia. */
    val scheduledHabitIds: Set<Long>
) {
    val dateString: String get() = date.toString()
    val ratio: Float get() = if (scheduledCount == 0) 0f else completedCount.toFloat() / scheduledCount
}

data class CalendarMonthSummary(
    val month: YearMonth,
    /** Celdas vacias antes del dia 1, con semanas de lunes a domingo. */
    val leadingBlanks: Int,
    val days: List<CalendarDaySummary>,
    /** Dias perfectos del mes hasta hoy. */
    val perfectDays: Int,
    /** Dias perfectos seguidos que terminan hoy. Hoy suma solo si ya es perfecto; los dias libres no la cortan. */
    val perfectStreak: Int,
    /** Habitos cumplidos sobre programados, de los dias del mes hasta hoy (0 a 100). */
    val completionPercent: Int
) {
    fun dayOf(date: LocalDate): CalendarDaySummary? =
        if (YearMonth.from(date) == month) days.getOrNull(date.dayOfMonth - 1) else null
}

/**
 * Calculo puro del calendario mensual. No toca Android ni la base de datos,
 * asi que se prueba con JUnit simple.
 *
 * Reglas (las mismas que el Mapa de calor):
 * - Un habito esta hecho un dia si su registro llega a la meta.
 * - Un habito cuenta un dia si le toca por frecuencia (lista vacia = todos los dias)
 *   y el dia no es anterior a su creacion. Si se hizo un dia que no tocaba, tambien cuenta.
 */
object CalendarMonthCalculator {

    /** Limite de dias hacia atras al medir la racha, para no recorrer historiales enormes. */
    private const val MAX_STREAK_LOOKBACK_DAYS = 730L

    fun build(
        habits: List<Habit>,
        logs: List<HabitLog>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): CalendarMonthSummary {
        val habitIds = habits.map { it.id }.toSet()
        val valuesByHabit: Map<Long, Map<String, Float>> = logs
            .filter { it.habitId in habitIds }
            .groupBy { it.habitId }
            .mapValues { (_, habitLogs) -> habitLogs.associate { it.date to it.value } }
        val createdDays: Map<Long, LocalDate> = habits.associate { it.id to createdDayOf(it, zone) }

        fun summarize(date: LocalDate): CalendarDaySummary {
            val dateString = date.toString()
            val dayOfWeek = date.dayOfWeek.value // 1 = lunes, 7 = domingo
            var scheduled = 0
            var completed = 0
            var hasProgress = false
            val ids = LinkedHashSet<Long>()
            for (habit in habits) {
                val value = valuesByHabit[habit.id]?.get(dateString) ?: 0f
                val target = if (habit.targetValue > 0f) habit.targetValue else 1f
                val isDone = value >= target
                val isProgrammed = !date.isBefore(createdDays.getValue(habit.id)) &&
                    (habit.frequencyDays.isEmpty() || dayOfWeek in habit.frequencyDays)
                if (!isDone && !isProgrammed) continue
                scheduled++
                ids += habit.id
                if (isDone) completed++ else if (value > 0f) hasProgress = true
            }
            val isToday = date == today
            val kind = when {
                date.isAfter(today) -> CalendarDayKind.FUTURE
                scheduled == 0 -> CalendarDayKind.FREE
                completed == scheduled -> CalendarDayKind.PERFECT
                completed > 0 || hasProgress || isToday -> CalendarDayKind.PARTIAL
                else -> CalendarDayKind.MISSED
            }
            return CalendarDaySummary(
                date = date,
                scheduledCount = scheduled,
                completedCount = completed,
                kind = kind,
                isToday = isToday,
                scheduledHabitIds = ids
            )
        }

        val days = (1..month.lengthOfMonth()).map { summarize(month.atDay(it)) }
        val elapsed = days.filter { !it.date.isAfter(today) }
        // Hoy solo suma lo ya cumplido: lo pendiente no baja el porcentaje mientras el dia siga abierto
        val scheduledSum = elapsed.sumOf { if (it.isToday) it.completedCount else it.scheduledCount }
        val completedSum = elapsed.sumOf { it.completedCount }
        val completionPercent = if (scheduledSum == 0) 0 else (completedSum * 100f / scheduledSum).roundToInt()

        // Primer dia con datos: creacion del habito mas antiguo o primer registro, lo que sea antes
        val firstLogDay = valuesByHabit.values
            .flatMap { it.keys }
            .minOrNull()
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val firstDay = listOfNotNull(createdDays.values.minOrNull(), firstLogDay).minOrNull()

        var streak = 0
        if (firstDay != null) {
            val limit = today.minusDays(MAX_STREAK_LOOKBACK_DAYS)
            var date = today
            while (!date.isBefore(firstDay) && !date.isBefore(limit)) {
                val day = summarize(date)
                when (day.kind) {
                    CalendarDayKind.PERFECT -> streak++
                    CalendarDayKind.FREE -> Unit
                    else -> if (date != today) break
                }
                date = date.minusDays(1)
            }
        }

        return CalendarMonthSummary(
            month = month,
            leadingBlanks = month.atDay(1).dayOfWeek.value - 1,
            days = days,
            perfectDays = elapsed.count { it.kind == CalendarDayKind.PERFECT },
            perfectStreak = streak,
            completionPercent = completionPercent
        )
    }

    private fun createdDayOf(habit: Habit, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
}
