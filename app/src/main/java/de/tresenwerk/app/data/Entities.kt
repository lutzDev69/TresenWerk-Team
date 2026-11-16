package de.tresenwerk.app.data

import androidx.room.ColumnInfo
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
    /** Anzahl Flaschen dieses Produkts inkl. der angebrochenen; [fillPercent] gilt für die angebrochene. Seit Version 4. */
    @ColumnInfo(defaultValue = "1") val bottleCount: Int = 1,
)

fun Bottle.displayName(): String = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ")

/** Ab hier gilt eine Flasche als "nachkaufen" (wenn keine volle mehr in Reserve ist). */
const val LOW_STOCK_PERCENT = 20

/** Noch etwas da: eine volle Reserveflasche oder eine nicht leere angebrochene. */
fun Bottle.hasStock(): Boolean = bottleCount >= 2 || (bottleCount == 1 && fillPercent > 0)

/** Nachkaufen, wenn nur noch die angebrochene Flasche da ist und sie fast leer ist. */
fun Bottle.needsRestock(threshold: Int = LOW_STOCK_PERCENT): Boolean =
    bottleCount <= 0 || (bottleCount == 1 && fillPercent <= threshold)

/** Gesamte Restmenge in ml: volle Reserveflaschen plus Rest der angebrochenen. */
fun Bottle.totalMl(): Int = if (bottleCount <= 0) 0 else (bottleCount - 1) * sizeMl + sizeMl * fillPercent / 100

/** Kurzer Bestandstext, z. B. "40 %", "40 % + 2 volle" oder "leer". */
fun Bottle.stockLabel(): String = when {
    bottleCount <= 0 -> "leer"
    bottleCount == 1 -> "$fillPercent %"
    bottleCount == 2 -> "$fillPercent % + 1 volle"
    else -> "$fillPercent % + ${bottleCount - 1} volle"
}
