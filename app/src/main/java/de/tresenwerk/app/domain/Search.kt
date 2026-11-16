package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.data.displayName
import de.tresenwerk.app.data.tagList

object RecipeFilter {
    /**
     * Mehrere Suchwörter müssen alle vorkommen ("gin limette" findet nur Rezepte mit beidem).
     * Gesucht wird in Name, Zutaten, Tags, Kategorie, Methode, Glas, Garnitur und Notizen.
     */
    fun apply(
        recipes: List<RecipeWithIngredients>,
        query: String,
        favoritesOnly: Boolean = false,
        method: String? = null,
        tag: String? = null,
    ): List<RecipeWithIngredients> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return recipes.filter { r ->
            (!favoritesOnly || r.recipe.isFavorite) &&
                (method == null || r.recipe.method.equals(method, ignoreCase = true)) &&
                (tag == null || r.recipe.tagList().any { it.equals(tag, ignoreCase = true) }) &&
                (terms.isEmpty() || haystack(r).let { h -> terms.all { it in h } })
        }
    }

    private fun haystack(r: RecipeWithIngredients): String = buildString {
        val x = r.recipe
        listOf(x.name, x.category, x.method, x.glass, x.garnish, x.tags, x.notes).forEach { append(it).append(' ') }
        r.ingredients.forEach { append(it.name).append(' ') }
    }.lowercase()
}

object Stock {
    /** Sucht eine passende Flasche im Bestand für eine Zutat (einfacher Textvergleich). */
    fun find(ingredientName: String, bottles: List<Bottle>): Bottle? {
        val needle = ingredientName.trim().lowercase()
        if (needle.length < 3) return null
        return bottles
            .filter { b ->
                val names = listOf(b.name, b.displayName(), b.category).map { it.lowercase() }.filter { it.length >= 3 }
                names.any { it.contains(needle) || needle.contains(it) }
            }
            .maxByOrNull { it.fillPercent }
    }
}
