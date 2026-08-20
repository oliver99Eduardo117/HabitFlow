package com.example.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.database.AppDatabase
import com.example.model.Category
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `toggleHabitCompletion unlocking streak badges for 3, 7, 14, and 30 days`() = runTest {
        val today = DateUtils.getTodayDateString()

        // 1. Streak 3 days
        val habit3 = repository.saveHabit(Habit(title = "Hábito 3 días", category = "Salud"))
        database.habitLogDao().insertOrUpdateLog(HabitLog(habitId = habit3, date = DateUtils.getDaysAgoDateString(2), value = 1f))
        database.habitLogDao().insertOrUpdateLog(HabitLog(habitId = habit3, date = DateUtils.getDaysAgoDateString(1), value = 1f))
        repository.toggleHabitCompletion(habit3, today)
        var stats = database.userStatsDao().getUserStats()
        assertTrue("streak_3 should be unlocked", stats?.unlockedBadgeIds?.contains("streak_3") == true)

        // 2. Streak 7 days
        val habit7 = repository.saveHabit(Habit(title = "Hábito 7 días", category = "Salud"))
        for (i in 1..6) {
            database.habitLogDao().insertOrUpdateLog(HabitLog(habitId = habit7, date = DateUtils.getDaysAgoDateString(i), value = 1f))
        }
        repository.toggleHabitCompletion(habit7, today)
        stats = database.userStatsDao().getUserStats()
        assertTrue("streak_7 should be unlocked", stats?.unlockedBadgeIds?.contains("streak_7") == true)

        // 3. Streak 14 days
        val habit14 = repository.saveHabit(Habit(title = "Hábito 14 días", category = "Salud"))
        for (i in 1..13) {
            database.habitLogDao().insertOrUpdateLog(HabitLog(habitId = habit14, date = DateUtils.getDaysAgoDateString(i), value = 1f))
        }
        repository.toggleHabitCompletion(habit14, today)
        stats = database.userStatsDao().getUserStats()
        assertTrue("streak_14 should be unlocked", stats?.unlockedBadgeIds?.contains("streak_14") == true)

        // 4. Streak 30 days
        val habit30 = repository.saveHabit(Habit(title = "Hábito 30 días", category = "Salud"))
        for (i in 1..29) {
            database.habitLogDao().insertOrUpdateLog(HabitLog(habitId = habit30, date = DateUtils.getDaysAgoDateString(i), value = 1f))
        }
        repository.toggleHabitCompletion(habit30, today)
        stats = database.userStatsDao().getUserStats()
        assertTrue("streak_30 should be unlocked", stats?.unlockedBadgeIds?.contains("streak_30") == true)
    }

    @Test
    fun `addFocusSession unlocks focus apprentice at 60 minutes and focus master at 180 minutes`() = runTest {
        // Accumulate 60 minutes
        repository.addFocusSession(60)
        var stats = database.userStatsDao().getUserStats()
        assertEquals(60, stats?.totalFocusMinutes)
        assertTrue(stats?.unlockedBadgeIds?.contains("focus_apprentice") == true)
        assertFalse(stats?.unlockedBadgeIds?.contains("focus_master") == true)

        // Accumulate 120 more minutes to reach 180 total
        repository.addFocusSession(120)
        stats = database.userStatsDao().getUserStats()
        assertEquals(180, stats?.totalFocusMinutes)
        assertTrue(stats?.unlockedBadgeIds?.contains("focus_apprentice") == true)
        assertTrue(stats?.unlockedBadgeIds?.contains("focus_master") == true)
    }

    @Test
    fun `toggleHabitCompletion twice on same date correctly awards and then deducts XP`() = runTest {
        val habitId = repository.saveHabit(
            Habit(
                title = "Lectura",
                category = "Productividad",
                targetValue = 1f
            )
        )
        val today = DateUtils.getTodayDateString()

        // First toggle: completes habit, awards 25 XP
        val earnedXp = repository.toggleHabitCompletion(habitId, today)
        assertEquals(25, earnedXp)

        var stats = database.userStatsDao().getUserStats()
        assertEquals(25, stats?.xp)
        assertEquals(1, stats?.totalCheckIns)

        // Second toggle: unchecks habit, deducts 25 XP
        val secondResult = repository.toggleHabitCompletion(habitId, today)
        assertEquals(0, secondResult)

        stats = database.userStatsDao().getUserStats()
        assertEquals(0, stats?.xp)
        assertEquals(0, stats?.totalCheckIns)
    }

    @Test
    fun `getHabitsWithStats computes isDependencyMet accurately based on dependency completion`() = runTest {
        val today = DateUtils.getTodayDateString()

        val prerequisiteHabitId = repository.saveHabit(
            Habit(
                title = "Preparar café",
                category = "Rutina",
                targetValue = 1f
            )
        )

        val dependentHabitId = repository.saveHabit(
            Habit(
                title = "Sesión de estudio",
                category = "Estudio",
                targetValue = 1f,
                dependencyHabitId = prerequisiteHabitId
            )
        )

        // Initially prerequisite habit is NOT completed on today
        var habitsWithStats = repository.getHabitsWithStats(today).first()
        var dependentItem = habitsWithStats.first { it.habit.id == dependentHabitId }
        assertFalse("Dependency should NOT be met before prerequisite is completed", dependentItem.isDependencyMet)

        // Complete prerequisite habit on today
        repository.toggleHabitCompletion(prerequisiteHabitId, today)

        // Now dependency should be met
        habitsWithStats = repository.getHabitsWithStats(today).first()
        dependentItem = habitsWithStats.first { it.habit.id == dependentHabitId }
        assertTrue("Dependency should be met after prerequisite is completed", dependentItem.isDependencyMet)
    }

    @Test
    fun `generateSmartInsights returns honest local insights when AI is disabled`() = runTest {
        // AI is disabled by default
        val habitId = repository.saveHabit(
            Habit(
                title = "Meditación",
                category = "Bienestar",
                targetValue = 1f
            )
        )
        database.habitLogDao().insertOrUpdateLog(HabitLog(habitId = habitId, date = DateUtils.getTodayDateString(), value = 1f))

        val insights = repository.generateSmartInsights()
        assertTrue("Should return insights", insights.isNotEmpty())
        assertTrue("Should contain check-ins count", insights.any { it.contains("check-ins reales") })
        assertTrue("Should identify anchor habit", insights.any { it.contains("Meditación") })
        // Ensure no fake 78% is present
        assertFalse("Should not have fake made-up correlation percentage", insights.any { it.contains("78%") })
    }

    @Test
    fun `checkStreakMilestone awards bonus XP and prevents duplicate milestone claims on retoggle`() = runTest {
        val habitId = repository.saveHabit(
            Habit(
                title = "Lectura",
                category = "Crecimiento",
                targetValue = 1f
            )
        )
        val today = DateUtils.getTodayDateString()

        // 1. 7 days completion -> milestone for 7 days
        for (i in 0..6) {
            database.habitLogDao().insertOrUpdateLog(
                HabitLog(
                    habitId = habitId,
                    date = DateUtils.getDaysAgoDateString(i),
                    value = 1f
                )
            )
        }

        val milestone = repository.checkStreakMilestone(habitId)
        org.junit.Assert.assertNotNull(milestone)
        assertEquals(7, milestone?.streakDays)
        assertEquals(100, milestone?.xpBonus)

        val statsAfterFirstClaim = database.userStatsDao().getUserStats()
        val xpAfterFirstClaim = statsAfterFirstClaim?.xp ?: 0
        assertTrue("Milestone XP bonus should be awarded", xpAfterFirstClaim >= 100)

        // 2. Exploit simulation: Unmark (delete log) and remark today's habit
        database.habitLogDao().deleteLog(habitId, today)
        database.habitLogDao().insertOrUpdateLog(
            HabitLog(
                habitId = habitId,
                date = today,
                value = 1f
            )
        )

        // Calling checkStreakMilestone again on same 7-day streak must NOT award bonus again
        val duplicateMilestone = repository.checkStreakMilestone(habitId)
        org.junit.Assert.assertNull("Duplicate milestone event should be null", duplicateMilestone)

        val statsAfterDuplicateAttempt = database.userStatsDao().getUserStats()
        assertEquals("XP total must not increase on duplicate milestone claim", xpAfterFirstClaim, statsAfterDuplicateAttempt?.xp)

        // 3. Genuine broken streak: user has a gap and starts a new streak from day 1
        for (i in 0..6) {
            database.habitLogDao().deleteLog(habitId, DateUtils.getDaysAgoDateString(i))
        }

        // Day 1 of new streak
        database.habitLogDao().insertOrUpdateLog(
            HabitLog(
                habitId = habitId,
                date = today,
                value = 1f
            )
        )
        // Check milestone on day 1 (resets lastMilestoneStreakClaimed = 0, returns null)
        val day1Milestone = repository.checkStreakMilestone(habitId)
        org.junit.Assert.assertNull("No milestone for day 1", day1Milestone)

        // User continues streak for day 2 and day 3
        database.habitLogDao().insertOrUpdateLog(
            HabitLog(
                habitId = habitId,
                date = DateUtils.getDaysAgoDateString(1),
                value = 1f
            )
        )
        database.habitLogDao().insertOrUpdateLog(
            HabitLog(
                habitId = habitId,
                date = DateUtils.getDaysAgoDateString(2),
                value = 1f
            )
        )

        val newStreakMilestone = repository.checkStreakMilestone(habitId)
        org.junit.Assert.assertNotNull("New streak milestone for 3 days should be awarded", newStreakMilestone)
        assertEquals(3, newStreakMilestone?.streakDays)
        assertEquals(50, newStreakMilestone?.xpBonus)

        val finalStats = database.userStatsDao().getUserStats()
        assertEquals(xpAfterFirstClaim + 50, finalStats?.xp)
    }
}
