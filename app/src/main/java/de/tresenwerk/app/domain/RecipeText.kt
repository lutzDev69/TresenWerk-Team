package de.tresenwerk.app.domain

import de.tresenwerk.app.data.RecipeWithIngredients

/** Rezept als lesbarer Text zum Teilen (Messenger, Mail …). */
object RecipeText {
    fun format(data: RecipeWithIngredients): String = buildString {
        val r = data.recipe
        appendLine(r.name)
        appendLine()
        data.sortedIngredients().forEach {
            appendLine("• " + formatIngredient(it.amount, Measure.fromStored(it.unit), it.name))
        }
        val facts = listOf("Methode" to r.method, "Glas" to r.glass, "Eis" to r.ice, "Garnitur" to r.garnish)
            .filter { it.second.isNotBlank() }
        if (facts.isNotEmpty()) {
            appendLine()
            facts.forEach { (k, v) -> appendLine("$k: $v") }
        }
        if (r.instructions.isNotBlank()) {
            appendLine()
            appendLine(r.instructions.trim())
        }
        if (r.notes.isNotBlank()) {
            appendLine()
            appendLine("Notizen: ${r.notes.trim()}")
        }
    }.trimEnd()
}
