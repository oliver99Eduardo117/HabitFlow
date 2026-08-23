package com.example.database

import androidx.room.*
import com.example.model.Habit
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY orderIndex ASC, id ASC")
    fun getActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY orderIndex ASC, id ASC")
    fun getArchivedHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits ORDER BY orderIndex ASC, id ASC")
    fun getAllHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Long): Habit?

    @Query("SELECT * FROM habits WHERE parentHabitId = :parentId")
    fun getChildHabits(parentId: Long): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("UPDATE habits SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchivedStatus(id: Long, isArchived: Boolean)

    @Query("UPDATE habits SET orderIndex = :newOrder WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, newOrder: Int)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: Long)

    @Query("UPDATE habits SET category = :newCategory WHERE category = :oldCategory")
    suspend fun updateHabitsCategory(oldCategory: String, newCategory: String)

    @Query("SELECT COUNT(*) FROM habits WHERE category = :categoryName")
    suspend fun getHabitsCountByCategory(categoryName: String): Int

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<Habit>)
}
