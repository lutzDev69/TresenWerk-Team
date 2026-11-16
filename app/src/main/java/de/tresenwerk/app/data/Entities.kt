package de.tresenwerk.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Ein Drink-Rezept (Spec). */
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",     // z. B. Basis-Spirituose oder Familie (Sour, Highball …)
    val glass: String = "",
    val ice: String = "",
    val method: String = "",       // Shaken, Stirred, Built …
    val garnish: String = "",
    val instructions: String = "",
    val notes: String = "",
    val tags: String = "",         // mit Komma getrennt, z. B. "Signature, Herbst"
    val photoPath: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

fun Recipe.tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Eine Zutat eines Rezepts. [unit] enthält den Namen einer [de.tresenwerk.app.domain.Measure]. */
@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val position: Int = 0,
    val name: String,
    val amount: Double?,
    val unit: String,
)

data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<Ingredient>,
) {
    fun sortedIngredients(): List<Ingredient> = ingredients.sortedBy { it.position }
}

/** Eine Flasche im Bestand der Bar. */
@Entity(tableName = "bottles")
data class Bottle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String = "",
    val category: String = "",
    val sizeMl: Int = 700,
    val fillPercent: Int = 100,
    val abv: Double? = null,
    val labelText: String = "",    // vollständiger vom Etikett gelesener Text
    val photoPath: String? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val barcode: String? = null,   // EAN/UPC, seit Datenbank-Version 2
    val price: Double? = null,     // Einkaufspreis pro Flasche in €, seit Datenbank-Version 3
)

fun Bottle.displayName(): String = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ")
