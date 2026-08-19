package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationConfigTest {

    @Test
    fun `calculateLevel tier 1 boundaries`() {
        assertEquals(1, GamificationConfig.calculateLevel(0))
        assertEquals(1, GamificationConfig.calculateLevel(50))
        assertEquals(1, GamificationConfig.calculateLevel(99))
    }

    @Test
    fun `calculateLevel tier 2 boundaries`() {
        assertEquals(2, GamificationConfig.calculateLevel(100))
        assertEquals(2, GamificationConfig.calculateLevel(175))
        assertEquals(2, GamificationConfig.calculateLevel(249))
    }

    @Test
    fun `calculateLevel tier 3 boundaries`() {
        assertEquals(3, GamificationConfig.calculateLevel(250))
        assertEquals(3, GamificationConfig.calculateLevel(350))
        assertEquals(3, GamificationConfig.calculateLevel(449))
    }

    @Test
    fun `calculateLevel tier 4 boundaries`() {
        assertEquals(4, GamificationConfig.calculateLevel(450))
        assertEquals(4, GamificationConfig.calculateLevel(575))
        assertEquals(4, GamificationConfig.calculateLevel(699))
    }

    @Test
    fun `calculateLevel tier 5 boundaries`() {
        assertEquals(5, GamificationConfig.calculateLevel(700))
        assertEquals(5, GamificationConfig.calculateLevel(875))
        assertEquals(5, GamificationConfig.calculateLevel(1049))
    }

    @Test
    fun `calculateLevel tier 6 boundaries`() {
        assertEquals(6, GamificationConfig.calculateLevel(1050))
        assertEquals(6, GamificationConfig.calculateLevel(1275))
        assertEquals(6, GamificationConfig.calculateLevel(1499))
    }

    @Test
    fun `calculateLevel tier 7 boundaries`() {
        assertEquals(7, GamificationConfig.calculateLevel(1500))
        assertEquals(7, GamificationConfig.calculateLevel(1775))
        assertEquals(7, GamificationConfig.calculateLevel(2049))
    }

    @Test
    fun `calculateLevel tier 8 boundaries`() {
        assertEquals(8, GamificationConfig.calculateLevel(2050))
        assertEquals(8, GamificationConfig.calculateLevel(2400))
        assertEquals(8, GamificationConfig.calculateLevel(2749))
    }

    @Test
    fun `calculateLevel tier 9 boundaries`() {
        assertEquals(9, GamificationConfig.calculateLevel(2750))
        assertEquals(9, GamificationConfig.calculateLevel(3175))
        assertEquals(9, GamificationConfig.calculateLevel(3599))
    }

    @Test
    fun `calculateLevel tier 10 boundaries`() {
        assertEquals(10, GamificationConfig.calculateLevel(3600))
        assertEquals(10, GamificationConfig.calculateLevel(4300))
        assertEquals(10, GamificationConfig.calculateLevel(4999))
    }

    @Test
    fun `calculateLevel beyond 5000 XP extrapolated levels`() {
        assertEquals(10, GamificationConfig.calculateLevel(5000))
        assertEquals(10, GamificationConfig.calculateLevel(5999))
        assertEquals(11, GamificationConfig.calculateLevel(6000))
        assertEquals(12, GamificationConfig.calculateLevel(7500))
        assertEquals(15, GamificationConfig.calculateLevel(10000))
    }
}
