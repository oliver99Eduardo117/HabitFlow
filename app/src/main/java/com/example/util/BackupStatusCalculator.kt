package com.example.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Que tan al dia esta la copia de seguridad. Define el color de la tarjeta de Ajustes. */
enum class BackupLevel {
    /** Copia de hace 7 dias o menos. Verde. */
    GOOD,

    /** Copia de 8 a 30 dias, o nunca hubo copia y todavia no hay registros. Ambar. */
    WARNING,

    /** Copia de mas de 30 dias, o nunca hubo copia y ya hay registros. Rojo. */
    DANGER
}

/** Estado de la copia con los textos ya resueltos: la interfaz solo los muestra. */
data class BackupStatus(
    val level: BackupLevel,
    /** Dias de calendario desde la ultima copia; null si nunca hubo una. */
    val daysSince: Int?,
    /** Titular de la tarjeta: "Última copia hace 12 días". */
    val headline: String,
    /** Frase bajo el titular. */
    val detail: String,
    /** Valor corto para la fila de Ajustes: "Hoy", "Ayer", "Hace 12 días", "Nunca". */
    val shortLabel: String,
    /** Fecha y hora de la ultima copia ("18 de septiembre, 21:40"); null si nunca hubo una. */
    val lastBackupLabel: String?,
    /** Pastilla de la pantalla Copias de seguridad: "Hace 12 días · conviene hacer otra". */
    val badge: String
)

/**
 * Calculo puro del estado de la copia. No toca Android, asi que se prueba con JUnit simple.
 *
 * Reglas:
 * - Se cuentan dias de calendario en la zona del telefono: una copia de anoche a las 23:30 es "ayer".
 * - 0 a 7 dias: GOOD. 8 a 30: WARNING. Mas de 30: DANGER.
 * - Sin copia: DANGER si ya hay registros que perder; WARNING si todavia no.
 */
object BackupStatusCalculator {

    const val GOOD_MAX_DAYS = 7
    const val WARNING_MAX_DAYS = 30

    private val DateTimeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-ES"))

    /** "18 de septiembre, 21:40" en la zona del telefono. */
    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun compute(
        lastBackupAtMillis: Long?,
        hasProgress: Boolean,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): BackupStatus {
        if (lastBackupAtMillis == null || lastBackupAtMillis <= 0L) {
            return BackupStatus(
                level = if (hasProgress) BackupLevel.DANGER else BackupLevel.WARNING,
                daysSince = null,
                headline = "Aún no tienes ninguna copia",
                detail = if (hasProgress) {
                    "Si pierdes o cambias de teléfono, podrías perder tu progreso."
                } else {
                    "Haz una para no perder tus rachas si cambias de teléfono."
                },
                shortLabel = "Nunca",
                lastBackupLabel = null,
                badge = if (hasProgress) "Sin copias · haz una hoy" else "Sin copias todavía"
            )
        }

        val backupTime = Instant.ofEpochMilli(lastBackupAtMillis).atZone(zone)
        // Si el reloj del telefono se atraso, la copia queda "en el futuro": se trata como de hoy
        val days = ChronoUnit.DAYS.between(backupTime.toLocalDate(), today).toInt().coerceAtLeast(0)
        val level = when {
            days <= GOOD_MAX_DAYS -> BackupLevel.GOOD
            days <= WARNING_MAX_DAYS -> BackupLevel.WARNING
            else -> BackupLevel.DANGER
        }
        val relative = when (days) {
            0 -> "hoy"
            1 -> "ayer"
            else -> "hace $days días"
        }
        val shortLabel = relative.replaceFirstChar { it.uppercase() }
        val advice = when (level) {
            BackupLevel.GOOD -> "todo en orden"
            BackupLevel.WARNING -> "conviene hacer otra"
            BackupLevel.DANGER -> "haz una hoy"
        }
        return BackupStatus(
            level = level,
            daysSince = days,
            headline = "Última copia $relative",
            detail = when (level) {
                BackupLevel.GOOD -> "Todo en orden. Guarda otra de vez en cuando."
                BackupLevel.WARNING -> "Conviene guardar una copia nueva."
                BackupLevel.DANGER -> "Tu última copia ya es vieja. Guarda una nueva hoy."
            },
            shortLabel = shortLabel,
            lastBackupLabel = formatDateTime(lastBackupAtMillis, zone),
            badge = "$shortLabel · $advice"
        )
    }
}
