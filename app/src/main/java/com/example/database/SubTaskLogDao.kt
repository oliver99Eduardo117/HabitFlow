package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.SubTaskLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SubTaskLogDao {
    @Query("SELECT * FROM sub_task_logs WHERE date = :date")
    fun getLogsForDate(date: String): Flow<List<SubTaskLog>>

    @Query("SELECT * FROM sub_task_logs WHERE habitId = :habitId AND date = :date")
    suspend fun getLogsForHabitAndDate(habitId: Long, date: String): List<SubTaskLog>

    @Query("SELECT * FROM sub_task_logs")
    suspend fun getAllOnce(): List<SubTaskLog>

    /** Devuelve -1 si la sub-rutina ya estaba marcada en esa fecha. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: SubTaskLog): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<SubTaskLog>)

    @Query("DELETE FROM sub_task_logs WHERE subTaskId = :subTaskId AND date = :date")
    suspend fun delete(subTaskId: Long, date: String): Int

    @Query("DELETE FROM sub_task_logs WHERE subTaskId = :subTaskId")
    suspend fun deleteForSubTask(subTaskId: Long)

    @Query("DELETE FROM sub_task_logs WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)

    @Query("DELETE FROM sub_task_logs")
    suspend fun deleteAll()
}
