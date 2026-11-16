package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Ingredient

data class BatchLine(val name: String, val amount: Double?, val unit: Measure)

/** Ergebnis in der gewählten Anzeige-Einheit. [total] und [water] zählen nur umrechenbare Mengen. */
data class BatchResult(val lines: List<BatchLine>, val total: Double, val water: Double, val unit: Measure)

/**
 * Rechnet ein Rezept auf mehrere Portionen hoch (Pre-Batch) und rechnet ml/cl/oz um.
 * Die Verdünnung (Wasser in % der Flüssigkeit) ersetzt beim Pre-Batch das Schmelzwasser vom Rühren/Shaken.
 */
object BatchCalculator {
    fun scale(
        ingredients: List<Ingredient>,
        portions: Int,
        dilutionPercent: Double,
        displayUnit: Measure,
    ): BatchResult {
        val factor = requireNotNull(displayUnit.ml) { "Anzeige-Einheit muss ein Volumen sein" }
        val n = portions.coerceAtLeast(1)
        var totalMl = 0.0
        val lines = ingredients.sortedBy { it.position }.map { ing ->
            val unit = Measure.fromStored(ing.unit)
            val amount = ing.amount
            val unitMl = unit.ml
            if (amount != null && unitMl != null) {
                val ml = amount * unitMl * n
                totalMl += ml
                BatchLine(ing.name, ml / factor, displayUnit)
            } else {
                BatchLine(ing.name, amount?.times(n), unit)
            }
        }
        val waterMl = totalMl * dilutionPercent.coerceAtLeast(0.0) / 100.0
        return BatchResult(lines, totalMl / factor, waterMl / factor, displayUnit)
    }
}
