package de.tresenwerk.app.data

import de.tresenwerk.app.domain.Measure

/** Drei Klassiker zum Ausprobieren der App. */
object SampleRecipes {
    private fun ing(amount: Double?, unit: Measure, name: String) = Ingredient(name = name, amount = amount, unit = unit.name)

    val all: List<RecipeWithIngredients> = listOf(
        RecipeWithIngredients(
            Recipe(
                name = "Daiquiri", category = "Rum", method = "Shaken", glass = "Coupe", ice = "Ohne",
                garnish = "Limettenzeste", tags = "Klassiker, Sour",
                instructions = "Alle Zutaten mit Eis kräftig shaken, doppelt in die vorgekühlte Coupe abseihen.",
            ),
            listOf(ing(60.0, Measure.ML, "Weißer Rum"), ing(25.0, Measure.ML, "Limettensaft"), ing(20.0, Measure.ML, "Zuckersirup 1:1")),
        ),
        RecipeWithIngredients(
            Recipe(
                name = "Negroni", category = "Gin", method = "Stirred", glass = "Rocks", ice = "Großer Würfel",
                garnish = "Orangenzeste", tags = "Klassiker, Aperitivo",
                instructions = "Auf Eis kalt rühren, über einen großen Würfel abseihen, Zeste ausdrücken.",
            ),
            listOf(ing(30.0, Measure.ML, "Gin"), ing(30.0, Measure.ML, "Campari"), ing(30.0, Measure.ML, "Roter Wermut")),
        ),
        RecipeWithIngredients(
            Recipe(
                name = "Old Fashioned", category = "Whiskey", method = "Stirred", glass = "Rocks", ice = "Großer Würfel",
                garnish = "Orangenzeste", tags = "Klassiker",
                instructions = "Im Rührglas auf Eis kalt rühren, über einen großen Würfel abseihen.",
            ),
            listOf(ing(60.0, Measure.ML, "Bourbon"), ing(7.5, Measure.ML, "Demerara-Sirup"), ing(2.0, Measure.DASH, "Angostura Bitters")),
        ),
    )
}
