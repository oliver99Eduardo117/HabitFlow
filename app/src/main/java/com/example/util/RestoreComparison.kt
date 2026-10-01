package com.example.util

import com.example.repository.RestoreSummary

/** Una fila de la tabla "Ahora -> Copia" que se muestra antes de recuperar una copia. */
data class RestoreComparisonRow(
    val label: String,
    val current: Int,
    val incoming: Int
) {
    /** La copia trae menos que lo que hay ahora: algo se pierde. */
    val isLoss: Boolean get() = incoming < current
}

data class RestoreComparison(
    val rows: List<RestoreComparisonRow>,
    /** Aviso de lo que se perderia, o null si la copia no trae menos de nada. */
    val warning: String?
)

/**
 * Compara lo que hay en el telefono con una copia. Calculo puro, se prueba con JUnit simple.
 *
 * Las diferencias de conteo son un minimo: dos listas del mismo tamano pueden tener datos
 * distintos. Por eso el aviso dice "por lo menos".
 */
object RestoreComparisonBuilder {

    fun build(current: RestoreSummary, incoming: RestoreSummary): RestoreComparison {
        val rows = listOf(
            RestoreComparisonRow("Hábitos", current.habitsCount, incoming.habitsCount),
            RestoreComparisonRow("Registros", current.logsCount, incoming.logsCount),
            RestoreComparisonRow("Pasos", current.subTasksCount, incoming.subTasksCount),
            RestoreComparisonRow("Categorías", current.categoriesCount, incoming.categoriesCount),
            RestoreComparisonRow("Nivel", current.userLevel, incoming.userLevel)
        )
        return RestoreComparison(rows = rows, warning = warningFor(current, incoming))
    }

    private fun warningFor(current: RestoreSummary, incoming: RestoreSummary): String? {
        if (incoming.habitsCount == 0 && current.habitsCount > 0) {
            return "La copia no tiene hábitos. Si sigues, te quedarás sin tus hábitos y sus registros."
        }
        val losses = listOfNotNull(
            lossText(current.habitsCount - incoming.habitsCount, "hábito", "hábitos"),
            lossText(current.logsCount - incoming.logsCount, "registro", "registros"),
            lossText(current.subTasksCount - incoming.subTasksCount, "paso", "pasos"),
            lossText(current.categoriesCount - incoming.categoriesCount, "categoría", "categorías")
        )
        val levelDrops = incoming.userLevel < current.userLevel
        if (losses.isEmpty() && !levelDrops) return null

        val sentences = mutableListOf<String>()
        if (losses.isNotEmpty()) {
            sentences += "Con esta copia perderás por lo menos ${joinSpanish(losses)}."
        }
        if (levelDrops) {
            sentences += "Tu nivel bajará de ${current.userLevel} a ${incoming.userLevel}."
        }
        return sentences.joinToString(" ")
    }

    private fun lossText(difference: Int, singular: String, plural: String): String? = when {
        difference <= 0 -> null
        difference == 1 -> "1 $singular"
        else -> "$difference $plural"
    }

    /** "a", "a y b", "a, b y c". */
    private fun joinSpanish(items: List<String>): String =
        if (items.size == 1) items[0] else items.dropLast(1).joinToString(", ") + " y " + items.last()
}
