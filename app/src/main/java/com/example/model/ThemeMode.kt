package com.example.model

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    val displayName: String
        get() = when (this) {
            LIGHT -> "Modo Claro"
            DARK -> "Modo Oscuro"
            SYSTEM -> "Predeterminado del Sistema"
        }

    val description: String
        get() = when (this) {
            LIGHT -> "Interfaz luminosa con contraste limpio"
            DARK -> "Tema oscuro Slate para descanso visual"
            SYSTEM -> "Sincronizado con la apariencia de Android"
        }
}
