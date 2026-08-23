package com.example.widget

import androidx.annotation.DrawableRes
import com.example.R

object WidgetIconHelper {

    @DrawableRes
    fun getWidgetIconRes(iconName: String?): Int? {
        if (iconName.isNullOrBlank()) return null
        return when (iconName.lowercase().trim()) {
            // Salud & Deporte
            "fitness" -> R.drawable.ic_habit_fitness
            "directions_run" -> R.drawable.ic_habit_directions_run
            "directions_walk" -> R.drawable.ic_habit_directions_walk
            "directions_bike" -> R.drawable.ic_habit_directions_bike
            "pool" -> R.drawable.ic_habit_pool
            "sports_soccer" -> R.drawable.ic_habit_sports_soccer
            "sports_basketball" -> R.drawable.ic_habit_sports_basketball
            "sports_tennis" -> R.drawable.ic_habit_sports_tennis
            "bolt" -> R.drawable.ic_habit_bolt
            "accessibility" -> R.drawable.ic_habit_accessibility
            "favorite" -> R.drawable.ic_habit_favorite
            "medical_services" -> R.drawable.ic_habit_medical_services

            // Nutrición & Hidratación
            "water" -> R.drawable.ic_habit_water
            "coffee" -> R.drawable.ic_habit_coffee
            "restaurant" -> R.drawable.ic_habit_restaurant
            "lunch_dining" -> R.drawable.ic_habit_lunch_dining
            "local_cafe" -> R.drawable.ic_habit_local_cafe
            "kitchen" -> R.drawable.ic_habit_kitchen

            // Mente, Zen & Bienestar
            "meditation" -> R.drawable.ic_habit_meditation
            "psychology" -> R.drawable.ic_habit_psychology
            "spa" -> R.drawable.ic_habit_spa
            "sun" -> R.drawable.ic_habit_sun
            "moon" -> R.drawable.ic_habit_moon
            "wb_twilight" -> R.drawable.ic_habit_wb_twilight
            "nature" -> R.drawable.ic_habit_nature
            "sentiment_very_satisfied" -> R.drawable.ic_habit_sentiment_very_satisfied

            // Productividad & Trabajo
            "timer" -> R.drawable.ic_habit_timer
            "alarm" -> R.drawable.ic_habit_alarm
            "work" -> R.drawable.ic_habit_work
            "check_circle" -> R.drawable.ic_habit_check_circle
            "task_alt" -> R.drawable.ic_habit_task_alt
            "center_focus_strong" -> R.drawable.ic_habit_center_focus_strong
            "schedule" -> R.drawable.ic_habit_schedule
            "checklist" -> R.drawable.ic_habit_checklist

            // Estudio, Lectura & Tecnología
            "book" -> R.drawable.ic_habit_book
            "code" -> R.drawable.ic_habit_code
            "laptop" -> R.drawable.ic_habit_laptop
            "school" -> R.drawable.ic_habit_school
            "language" -> R.drawable.ic_habit_language
            "edit_note" -> R.drawable.ic_habit_edit_note
            "lightbulb" -> R.drawable.ic_habit_lightbulb
            "science" -> R.drawable.ic_habit_science

            // Creatividad & Hobbies
            "brush" -> R.drawable.ic_habit_brush
            "music" -> R.drawable.ic_habit_music
            "photo_camera" -> R.drawable.ic_habit_photo_camera
            "sports_esports" -> R.drawable.ic_habit_sports_esports
            "palette" -> R.drawable.ic_habit_palette
            "movie" -> R.drawable.ic_habit_movie

            // Finanzas & Crecimiento
            "savings" -> R.drawable.ic_habit_savings
            "payments" -> R.drawable.ic_habit_payments
            "trending_up" -> R.drawable.ic_habit_trending_up
            "account_balance" -> R.drawable.ic_habit_account_balance

            // Hogar, Social & Estilo de Vida
            "cleaning_services" -> R.drawable.ic_habit_cleaning_services
            "home" -> R.drawable.ic_habit_home
            "pets" -> R.drawable.ic_habit_pets
            "groups" -> R.drawable.ic_habit_groups
            "emoji_events", "trophy" -> R.drawable.ic_habit_emoji_events
            "star" -> R.drawable.ic_habit_star

            // Gamificación, Niveles & Insignias
            "sprout", "eco" -> R.drawable.ic_habit_sprout
            "local_fire_department", "fire", "flame" -> R.drawable.ic_habit_local_fire_department
            "military_tech", "medal" -> R.drawable.ic_habit_military_tech
            "shield" -> R.drawable.ic_habit_shield
            "auto_awesome" -> R.drawable.ic_habit_auto_awesome
            "workspace_premium", "badge" -> R.drawable.ic_habit_workspace_premium
            "diamond" -> R.drawable.ic_habit_diamond
            "stars" -> R.drawable.ic_habit_stars
            "rocket_launch", "rocket" -> R.drawable.ic_habit_rocket_launch

            else -> null
        }
    }
}
