package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.Ingredient

/** Kosten einer Zutat; [cost] ist null, wenn keine Flasche mit Preis passt oder die Menge unklar ist. */
data class CostLine(val name: String, val cost: Double?, val bottle: Bottle?)

data class CostResult(val lines: List<CostLine>, val total: Double, val withoutPrice: List<String>) {
    /** Richtwert für den Netto-Verkaufspreis bei einem gewünschten Wareneinsatz (z. B. 20 %). */
    fun sellingPrice(costPercent: Double): Double = if (costPercent > 0) total / (costPercent / 100.0) else 0.0
}

/** Wareneinsatz pro Drink: Menge × (Flaschenpreis / Flaschengröße). */
object Costing {
    fun calculate(ingredients: List<Ingredient>, bottles: List<Bottle>, portions: Int = 1): CostResult {
        val n = portions.coerceAtLeast(1)
        val lines = ingredients.sortedBy { it.position }.map { ing ->
            val bottle = Stock.find(ing.name, bottles)
            val perMl = bottle?.price?.takeIf { bottle.sizeMl > 0 }?.div(bottle.sizeMl)
            val ml = ing.amount?.let { a -> Measure.fromStored(ing.unit).approxMl?.times(a) }
            CostLine(ing.name, if (perMl != null && ml != null) perMl * ml * n else null, bottle)
        }
        return CostResult(
            lines = lines,
            total = lines.sumOf { it.cost ?: 0.0 },
            withoutPrice = lines.filter { it.cost == null }.map { it.name },
        )
    }
}
