package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Backup
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.Ingredient
import de.tresenwerk.app.data.Recipe
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.data.hasStock
import de.tresenwerk.app.data.needsRestock
import de.tresenwerk.app.data.stockLabel
import de.tresenwerk.app.data.totalMl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottleCountTest {
    @Test
    fun stockWithReserveBottles() {
        val campari = Bottle(name = "Campari", sizeMl = 700, fillPercent = 10, bottleCount = 3)
        assertEquals(1470, campari.totalMl())            // 2 volle à 700 ml + 70 ml
        assertEquals("10 % + 2 volle", campari.stockLabel())
        assertFalse(campari.needsRestock())               // Reserve vorhanden
        assertTrue(campari.copy(bottleCount = 1).needsRestock())
        assertEquals("leer", campari.copy(bottleCount = 0).stockLabel())
        assertTrue(campari.copy(bottleCount = 0).needsRestock())
    }

    @Test
    fun emptyOpenBottleWithReserveIsStillAvailable() {
        val gin = Bottle(name = "Gin", fillPercent = 0, bottleCount = 2)
        assertTrue(gin.hasStock())
        assertFalse(gin.copy(bottleCount = 1).hasStock())
        val gimlet = RecipeWithIngredients(Recipe(name = "Gimlet"), listOf(Ingredient(name = "Gin", amount = 60.0, unit = "ML")))
        assertTrue(Availability.missing(gimlet, listOf(gin)).isEmpty())
        assertEquals(listOf("Gin"), Availability.missing(gimlet, listOf(gin.copy(bottleCount = 1))))
    }

    @Test
    fun shoppingListSkipsProductsWithReserve() {
        val bottles = listOf(
            Bottle(name = "Campari", fillPercent = 10, bottleCount = 2),
            Bottle(name = "Aperol", fillPercent = 15, bottleCount = 1),
        )
        val text = ShoppingList.format(bottles, 20, "1.1.")
        assertFalse(text.contains("Campari"))
        assertTrue(text.contains("☐ Aperol (700 ml) – noch 15 %"))
    }

    @Test
    fun backupKeepsCount() {
        val json = Backup.toJson(emptyList(), listOf(Bottle(name = "Campari", bottleCount = 4)))
        assertEquals(4, Backup.fromJson(json).second.single().bottleCount)
        // Ältere Sicherungen ohne Anzahl → 1 Flasche
        assertEquals(1, Backup.fromJson(json.replace("\"bottleCount\": 4,", "")).second.single().bottleCount)
    }
}
