package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BackupStatusCalculatorTest {

    // Cancun no tiene horario de verano: UTC-5 todo el ano
    private val zone = ZoneId.of("America/Cancun")
    private val today = LocalDate.of(2026, 9, 30)

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun status(lastBackup: Long?, hasProgress: Boolean = true) =
        BackupStatusCalculator.compute(lastBackup, hasProgress, today, zone)

    @Test
    fun `never backed up with progress is danger`() {
        val result = status(null, hasProgress = true)
        assertEquals(BackupLevel.DANGER, result.level)
        assertNull(result.daysSince)
        assertEquals("Aún no tienes ninguna copia", result.headline)
        assertEquals("Nunca", result.shortLabel)
        assertNull(result.lastBackupLabel)
        assertEquals("Sin copias · haz una hoy", result.badge)
    }

    @Test
    fun `never backed up without progress is only a warning`() {
        val result = status(null, hasProgress = false)
        assertEquals(BackupLevel.WARNING, result.level)
        assertEquals("Sin copias todavía", result.badge)
        assertEquals("Haz una para no perder tus rachas si cambias de teléfono.", result.detail)
    }

    @Test
    fun `zero or negative timestamp counts as never`() {
        assertEquals("Nunca", status(0L).shortLabel)
        assertEquals("Nunca", status(-5L).shortLabel)
    }

    @Test
    fun `backup today`() {
        val result = status(millisAt(2026, 9, 30, 8, 5))
        assertEquals(BackupLevel.GOOD, result.level)
        assertEquals(0, result.daysSince)
        assertEquals("Última copia hoy", result.headline)
        assertEquals("Hoy", result.shortLabel)
        assertEquals("Hoy · todo en orden", result.badge)
    }

    @Test
    fun `last night counts as yesterday by calendar day, not by 24 hours`() {
        // 23:30 en Cancun ya es el dia siguiente en UTC: el calculo debe usar la zona del telefono
        val result = status(millisAt(2026, 9, 29, 23, 30))
        assertEquals(1, result.daysSince)
        assertEquals("Última copia ayer", result.headline)
        assertEquals("Ayer", result.shortLabel)
        assertEquals("29 de septiembre, 23:30", result.lastBackupLabel)
    }

    @Test
    fun `seven days is still good and eight is a warning`() {
        assertEquals(BackupLevel.GOOD, status(millisAt(2026, 9, 23)).level)
        val eight = status(millisAt(2026, 9, 22))
        assertEquals(BackupLevel.WARNING, eight.level)
        assertEquals("Última copia hace 8 días", eight.headline)
        assertEquals("Hace 8 días · conviene hacer otra", eight.badge)
    }

    @Test
    fun `thirty days is a warning and thirty one is danger`() {
        assertEquals(BackupLevel.WARNING, status(millisAt(2026, 8, 31)).level)
        val old = status(millisAt(2026, 8, 30))
        assertEquals(BackupLevel.DANGER, old.level)
        assertEquals(31, old.daysSince)
        assertEquals("Hace 31 días", old.shortLabel)
        assertEquals("Tu última copia ya es vieja. Guarda una nueva hoy.", old.detail)
    }

    @Test
    fun `progress flag only matters when there is no backup`() {
        val withProgress = status(millisAt(2026, 9, 18, 21, 40), hasProgress = true)
        val withoutProgress = status(millisAt(2026, 9, 18, 21, 40), hasProgress = false)
        assertEquals(withProgress, withoutProgress)
        assertEquals("18 de septiembre, 21:40", withProgress.lastBackupLabel)
        assertEquals(12, withProgress.daysSince)
    }

    @Test
    fun `formatDateTime uses spanish month names and 24 hour clock`() {
        assertEquals("1 de enero, 07:05", BackupStatusCalculator.formatDateTime(millisAt(2026, 1, 1, 7, 5), zone))
        assertEquals("31 de diciembre, 22:00", BackupStatusCalculator.formatDateTime(millisAt(2026, 12, 31, 22, 0), zone))
    }

    @Test
    fun `backup in the future after a clock change is treated as today`() {
        val result = status(millisAt(2026, 10, 2))
        assertEquals(0, result.daysSince)
        assertEquals(BackupLevel.GOOD, result.level)
    }
}
