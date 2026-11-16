package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.Ingredient
import de.tresenwerk.app.data.Recipe
import de.tresenwerk.app.data.RecipeWithIngredients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarFeaturesTest {
    private val gin = Bottle(id = 1, name = "London Dry Gin", brand = "Tanqueray", category = "Gin", sizeMl = 700, fillPercent = 60, price = 21.0)
    private val campari = Bottle(id = 2, name = "Campari", category = "Bitter", sizeMl = 1000, fillPercent = 10, price = 16.0)
    private val bourbon = Bottle(id = 3, name = "Buffalo Trace", category = "Whisk(e)y", sizeMl = 700, fillPercent = 0, price = 35.0)
    private val bottles = listOf(gin, campari, bourbon)

    private fun recipe(vararg ing: Pair<String, Pair<Double?, Measure>>) = RecipeWithIngredients(
        Recipe(id = 1, name = "Test"),
        ing.mapIndexed { i, (name, a) -> Ingredient(position = i, name = name, amount = a.first, unit = a.second.name) },
    )

    @Test
    fun matchesWholeWordsAndCategories() {
        assertEquals(1L, Stock.find("Gin", bottles)?.id)
        assertNull(Stock.find("Ginger Beer", bottles))
        assertEquals(3L, Stock.find("Bourbon", bottles)?.id)      // über die Sorte
        assertEquals(2L, Stock.find("Campari", bottles)?.id)      // über den Namen
    }

    @Test
    fun pantryDetection() {
        assertTrue(Availability.isPantry("Limettensaft"))
        assertTrue(Availability.isPantry("Zuckersirup 1:1"))
        assertTrue(Availability.isPantry("Ginger Beer"))
        assertTrue(Availability.isPantry("Eiweiß"))
        assertFalse(Availability.isPantry("Orangenlikör"))
        assertFalse(Availability.isPantry("Weißer Rum"))
        assertFalse(Availability.isPantry("Angostura Bitters"))
    }

    @Test
    fun mixableRecipes() {
        val negroni = recipe("Gin" to (30.0 to Measure.ML), "Campari" to (30.0 to Measure.ML), "Roter Wermut" to (30.0 to Measure.ML))
        assertEquals(listOf("Roter Wermut"), Availability.missing(negroni, bottles))
        val gimlet = recipe("Gin" to (60.0 to Measure.ML), "Limettensaft" to (20.0 to Measure.ML), "Zuckersirup" to (15.0 to Measure.ML))
        assertTrue(Availability.missing(gimlet, bottles).isEmpty())
        // Leere Flasche zählt nicht
        val oldFashioned = recipe("Bourbon" to (60.0 to Measure.ML), "Demerara-Sirup" to (7.5 to Measure.ML))
        assertEquals(listOf("Bourbon"), Availability.missing(oldFashioned, bottles))
    }

    @Test
    fun costPerDrink() {
        val negroni = recipe(
            "Gin" to (3.0 to Measure.CL),
            "Campari" to (30.0 to Measure.ML),
            "Roter Wermut" to (30.0 to Measure.ML),
            "Angostura" to (2.0 to Measure.DASH),
        )
        val cost = Costing.calculate(negroni.ingredients, bottles)
        // Gin: 30 ml × 21 €/700 ml = 0,90 €; Campari: 30 ml × 16 €/1000 ml = 0,48 €
        assertEquals(1.38, cost.total, 0.001)
        assertEquals(listOf("Roter Wermut", "Angostura"), cost.withoutPrice)
        assertEquals(6.90, cost.sellingPrice(20.0), 0.001)
        assertEquals(2.76, Costing.calculate(negroni.ingredients, bottles, portions = 2).total, 0.001)
    }

    @Test
    fun shoppingList() {
        val text = ShoppingList.format(bottles, threshold = 20, date = "16.11.2026")
        assertTrue(text.contains("☐ Campari (1000 ml) – noch 10 % – zuletzt 16,00 €"))
        assertTrue(text.contains("☐ Buffalo Trace (700 ml) – noch 0 %"))
        assertFalse(text.contains("Tanqueray"))
        assertTrue(text.contains("Summe (bekannte Preise): 51,00 €"))
        assertEquals("Einkaufsliste 1.1.\n\nNichts nachzukaufen.", ShoppingList.format(listOf(gin), 20, "1.1."))
    }
}
