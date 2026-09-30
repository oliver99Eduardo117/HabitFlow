package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `getProgress uses the same ranges as calculateLevel`() {
        // El nivel 10 va de 3,600 a 5,999 XP; desde ahi, 1,000 XP por nivel
        GamificationConfig.getProgress(5000).let {
            assertEquals(10, it.currentLevel)
            assertEquals(3600, it.minXpForLevel)
            assertEquals(6000, it.maxXpForLevel)
            assertEquals(1000, it.xpNeededForNextLevel)
        }
        GamificationConfig.getProgress(6000).let {
            assertEquals(11, it.currentLevel)
            assertEquals(6000, it.minXpForLevel)
            assertEquals(7000, it.maxXpForLevel)
            assertEquals(0f, it.progressFraction, 0.0001f)
        }
        GamificationConfig.getProgress(10725).let {
            assertEquals(15, it.currentLevel)
            assertEquals(725, it.xpInCurrentLevel)
            assertEquals(275, it.xpNeededForNextLevel)
            assertEquals(0.725f, it.progressFraction, 0.0001f)
        }
        for (xp in 0..20000 step 25) {
            val progress = GamificationConfig.getProgress(xp)
            assertEquals(GamificationConfig.calculateLevel(xp), progress.currentLevel)
            assertTrue("$xp XP fuera de su rango", xp >= progress.minXpForLevel && xp < progress.maxXpForLevel)
        }
    }

    @Test
    fun `level title does not repeat the level number`() {
        assertEquals("Leyenda Suprema", GamificationConfig.getProgress(10725).levelTitle)
        assertEquals("Leyenda Suprema HabitFlow", GamificationConfig.getProgress(4000).levelTitle)
    }
}
