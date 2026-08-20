package com.example.widget

import java.util.concurrent.ConcurrentHashMap

data class HabitFeedbackState(
    val habitId: Long,
    val isOptimisticallyCompleted: Boolean,
    val isFlashing: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object WidgetFeedbackManager {
    private val activeFeedbacks = ConcurrentHashMap<Long, HabitFeedbackState>()
    private const val FLASH_DURATION_MS = 1600L

    fun setImmediateToggle(habitId: Long, wasCompleted: Boolean): HabitFeedbackState {
        val willBeCompleted = !wasCompleted
        val state = HabitFeedbackState(
            habitId = habitId,
            isOptimisticallyCompleted = willBeCompleted,
            isFlashing = true,
            timestamp = System.currentTimeMillis()
        )
        activeFeedbacks[habitId] = state
        return state
    }

    fun getFeedbackState(habitId: Long): HabitFeedbackState? {
        val state = activeFeedbacks[habitId] ?: return null
        if (System.currentTimeMillis() - state.timestamp > FLASH_DURATION_MS) {
            activeFeedbacks.remove(habitId)
            return null
        }
        return state
    }

    fun clearFeedback(habitId: Long) {
        activeFeedbacks.remove(habitId)
    }
}
