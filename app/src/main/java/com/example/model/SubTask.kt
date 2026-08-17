package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sub_tasks")
data class SubTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val date: String = "" // Optional date for daily subtasks
)
