package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.displayName

/** Einkaufsliste aus dem Bestand: alle Flaschen bis [threshold] % Füllstand, nach Sorte gruppiert. */
object ShoppingList {
    fun items(bottles: List<Bottle>, threshold: Int): List<Bottle> =
        bottles.filter { it.fillPercent <= threshold }
            .sortedWith(compareBy({ it.category.ifBlank { "~" }.lowercase() }, { it.displayName().lowercase() }))

    fun format(bottles: List<Bottle>, threshold: Int, date: String): String {
        val items = items(bottles, threshold)
        if (items.isEmpty()) return "Einkaufsliste $date\n\nNichts nachzukaufen."
        return buildString {
            appendLine("Einkaufsliste $date")
            items.groupBy { it.category.ifBlank { "Sonstiges" } }.forEach { (category, list) ->
                appendLine()
                appendLine(category)
                list.forEach { b ->
                    append("☐ ${b.displayName()} (${b.sizeMl} ml) – noch ${b.fillPercent} %")
                    b.price?.let { append(" – zuletzt ${formatEuro(it)}") }
                    appendLine()
                }
            }
            val sum = items.mapNotNull { it.price }.sum()
            if (sum > 0) {
                appendLine()
                append("Summe (bekannte Preise): ${formatEuro(sum)}")
            }
        }.trimEnd()
    }
}
