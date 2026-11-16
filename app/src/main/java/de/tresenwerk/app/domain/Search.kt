package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.data.displayName
import de.tresenwerk.app.data.tagList
import de.tresenwerk.app.scan.CategoryGuess

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
    /**
     * Sucht eine passende Flasche im Bestand für eine Zutat. Verglichen werden ganze Wörter:
     * "Gin" findet "Tanqueray London Dry Gin", aber "Ginger Beer" findet keinen Gin.
     */
    fun find(ingredientName: String, bottles: List<Bottle>): Bottle? {
        val needle = ingredientName.trim()
        if (needle.length < 3) return null
        // 1. Name/Marke passt, z. B. "Campari" oder "Tanqueray"
        bottles
            .filter { b ->
                listOf(b.name, b.displayName())
                    .filter { it.trim().length >= 3 }
                    .any { containsWords(it, needle) || containsWords(needle, it) }
            }
            .maxByOrNull { it.fillPercent }
            ?.let { return it }
        // 2. Sorte passt, z. B. "Weißer Rum" → Sorte Rum, "Bourbon" → Sorte Whisk(e)y
        val guessed = CategoryGuess.fromText(needle)
        return bottles
            .filter { b ->
                b.category.isNotBlank() &&
                    (b.category.equals(guessed, ignoreCase = true) || containsWords(needle, b.category))
            }
            .maxByOrNull { it.fillPercent }
    }

    /** true, wenn [words] als ganze Wortfolge in [text] vorkommt (Groß-/Kleinschreibung egal). */
    fun containsWords(text: String, words: String): Boolean =
        Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(words.trim()) + """(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}

/** Prüft, ob ein Rezept mit dem aktuellen Bestand gemixt werden kann. */
object Availability {
    // Frische Zutaten und Grundvorrat stehen nicht als Flasche im Bestand – sie gelten als vorhanden.
    private val pantryParts = listOf(
        "saft", "juice", "limette", "zitrone", "lime", "lemon", "orange", "grapefruit", "ananas", "pineapple",
        "maracuja", "passionsfrucht", "sirup", "syrup", "zucker", "sugar", "honig", "honey", "agave",
        "wasser", "water", "soda", "tonic", "ginger", "cola", "limonade", "minze", "mint", "basilikum",
        "rosmarin", "thymian", "gurke", "cucumber", "beere", "berry", "püree", "puree", "eiweiß", "aquafaba",
        "sahne", "cream", "milch", "milk", "kaffee", "espresso", "salz", "salt", "pfeffer", "zeste", "kirsche",
        "cherry", "olive", "frucht", "fruit", "tee", "zimt", "muskat",
    )
    private val pantryWords = setOf("ei", "eis", "egg", "ice", "eigelb")

    // Alkoholisches gehört in den Bestand, auch wenn "Orange" oder "Kirsche" im Namen steht
    private val spiritParts = listOf(
        "likör", "liqueur", "bitter", "wermut", "vermouth", "vermut", "curaçao", "curacao", "triple sec",
        "amaro", "aperol", "campari", "whisk", "bourbon", "vodka", "wodka", "tequila", "mezcal", "brandy",
        "cognac", "sherry", "absinth", "schnaps", "geist", "brand", "sekt", "champagner", "prosecco", "wein", "wine",
    )
    private val spiritWords = setOf("gin", "rum", "port", "pisco", "cachaça", "cachaca")

    fun isPantry(name: String): Boolean {
        val n = name.lowercase()
        val words = n.split(Regex("""[^\p{L}]+"""))
        if (spiritParts.any { it in n } || words.any { it in spiritWords }) return false
        return pantryParts.any { it in n } || words.any { it in pantryWords }
    }

    /** Zutaten, für die weder eine nicht-leere Flasche noch ein Grundvorrat passt. */
    fun missing(recipe: RecipeWithIngredients, bottles: List<Bottle>): List<String> =
        recipe.sortedIngredients().map { it.name }.filter { name ->
            val bottle = Stock.find(name, bottles)
            when {
                bottle != null && bottle.fillPercent > 0 -> false
                bottle == null && isPantry(name) -> false
                else -> true
            }
        }
}
