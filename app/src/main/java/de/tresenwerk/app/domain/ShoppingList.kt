package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.displayName
import de.tresenwerk.app.data.needsRestock
import de.tresenwerk.app.data.stockLabel

/** Einkaufsliste: Produkte ohne volle Reserveflasche, deren angebrochene Flasche bei höchstens [threshold] % ist. */
object ShoppingList {
    fun items(bottles: List<Bottle>, threshold: Int): List<Bottle> =
        bottles.filter { it.needsRestock(threshold) }
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
                    append("☐ ${b.displayName()} (${b.sizeMl} ml) – noch ${b.stockLabel()}")
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
