package com.example.database

import androidx.room.*
import com.example.model.SubTask
import kotlinx.coroutines.flow.Flow

@Dao
interface SubTaskDao {
    @Query("SELECT * FROM sub_tasks WHERE habitId = :habitId")
    fun getSubTasksForHabit(habitId: Long): Flow<List<SubTask>>

    @Query("SELECT * FROM sub_tasks")
    fun getAllSubTasks(): Flow<List<SubTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: SubTask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTask>)

    @Update
    suspend fun updateSubTask(subTask: SubTask)

    @Query("UPDATE sub_tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setSubTaskCompleted(id: Long, isCompleted: Boolean)

    @Query("DELETE FROM sub_tasks WHERE id = :id")
    suspend fun deleteSubTask(id: Long)

    @Query("DELETE FROM sub_tasks WHERE habitId = :habitId")
    suspend fun deleteSubTasksForHabit(habitId: Long)
}
