package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.Ingredient
import de.tresenwerk.app.data.Recipe
import de.tresenwerk.app.data.RecipeWithIngredients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {
    private fun recipe(id: Long, name: String, method: String, tags: String, fav: Boolean, vararg ing: String) =
        RecipeWithIngredients(
            Recipe(id = id, name = name, method = method, tags = tags, isFavorite = fav),
            ing.mapIndexed { i, n -> Ingredient(position = i, name = n, amount = 30.0, unit = "ML") },
        )

    private val all = listOf(
        recipe(1, "Gimlet", "Shaken", "Klassiker", false, "Gin", "Limettensaft"),
        recipe(2, "Negroni", "Stirred", "Klassiker, Aperitivo", true, "Gin", "Campari", "Wermut"),
        recipe(3, "Mojito", "Built", "Sommer", false, "Rum", "Limettensaft", "Minze"),
    )

    @Test
    fun allTermsMustMatch() {
        assertEquals(listOf(1L), RecipeFilter.apply(all, "gin limette").map { it.recipe.id })
        assertEquals(listOf(1L, 3L), RecipeFilter.apply(all, "LIMETTE").map { it.recipe.id })
    }

    @Test
    fun filtersCombine() {
        assertEquals(listOf(2L), RecipeFilter.apply(all, "", favoritesOnly = true).map { it.recipe.id })
        assertEquals(listOf(3L), RecipeFilter.apply(all, "", method = "built").map { it.recipe.id })
        assertEquals(listOf(1L, 2L), RecipeFilter.apply(all, "", tag = "klassiker").map { it.recipe.id })
    }

    @Test
    fun findsBottleForIngredient() {
        val bottles = listOf(
            Bottle(id = 1, name = "London Dry Gin", brand = "Tanqueray", fillPercent = 40),
            Bottle(id = 2, name = "Campari", fillPercent = 10),
        )
        assertEquals(1L, Stock.find("Gin", bottles)?.id)
        assertEquals(2L, Stock.find("campari", bottles)?.id)
        assertNull(Stock.find("Minze", bottles))
    }
}
