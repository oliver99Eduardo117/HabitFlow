package com.example.util

import com.example.repository.RestoreSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreComparisonTest {

    private fun summary(
        habits: Int = 8,
        logs: Int = 412,
        steps: Int = 14,
        categories: Int = 6,
        level: Int = 5
    ) = RestoreSummary(
        habitsCount = habits,
        logsCount = logs,
        subTasksCount = steps,
        categoriesCount = categories,
        userLevel = level,
        userXp = 0
    )

    private val now = summary()

    @Test
    fun `rows keep the order and labels of the table`() {
        val result = RestoreComparisonBuilder.build(now, now)
        assertEquals(listOf("Hábitos", "Registros", "Pasos", "Categorías", "Nivel"), result.rows.map { it.label })
        assertEquals(listOf(8, 412, 14, 6, 5), result.rows.map { it.current })
    }

    @Test
    fun `same counts give no warning`() {
        val result = RestoreComparisonBuilder.build(now, now)
        assertNull(result.warning)
        assertTrue(result.rows.none { it.isLoss })
    }

    @Test
    fun `a copy with more data is not a loss`() {
        val result = RestoreComparisonBuilder.build(now, summary(habits = 9, logs = 500, level = 6))
        assertNull(result.warning)
        assertFalse(result.rows.any { it.isLoss })
    }

    @Test
    fun `one loss uses the singular`() {
        val result = RestoreComparisonBuilder.build(now, summary(habits = 7))
        assertEquals("Con esta copia perderás por lo menos 1 hábito.", result.warning)
        assertTrue(result.rows[0].isLoss)
        assertFalse(result.rows[1].isLoss)
    }

    @Test
    fun `two losses are joined with y`() {
        val result = RestoreComparisonBuilder.build(now, summary(habits = 7, logs = 389))
        assertEquals("Con esta copia perderás por lo menos 1 hábito y 23 registros.", result.warning)
    }

    @Test
    fun `three or more losses use commas and y`() {
        val result = RestoreComparisonBuilder.build(now, summary(habits = 6, logs = 389, steps = 13, categories = 4))
        assertEquals(
            "Con esta copia perderás por lo menos 2 hábitos, 23 registros, 1 paso y 2 categorías.",
            result.warning
        )
    }

    @Test
    fun `level drop alone gets its own sentence`() {
        val result = RestoreComparisonBuilder.build(now, summary(level = 3))
        assertEquals("Tu nivel bajará de 5 a 3.", result.warning)
        assertTrue(result.rows[4].isLoss)
    }

    @Test
    fun `losses and level drop are both mentioned`() {
        val result = RestoreComparisonBuilder.build(now, summary(logs = 400, level = 4))
        assertEquals("Con esta copia perderás por lo menos 12 registros. Tu nivel bajará de 5 a 4.", result.warning)
    }

    @Test
    fun `a copy without habits gets the strongest warning`() {
        val result = RestoreComparisonBuilder.build(now, summary(habits = 0, logs = 0, steps = 0, level = 1))
        assertEquals("La copia no tiene hábitos. Si sigues, te quedarás sin tus hábitos y sus registros.", result.warning)
    }

    @Test
    fun `empty phone and empty copy give no warning`() {
        val empty = summary(habits = 0, logs = 0, steps = 0, categories = 0, level = 1)
        assertNull(RestoreComparisonBuilder.build(empty, empty).warning)
    }
}
