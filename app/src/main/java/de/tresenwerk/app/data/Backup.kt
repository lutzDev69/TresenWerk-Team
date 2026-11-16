package de.tresenwerk.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sicherung aller Rezepte und Flaschen als JSON. Fotos werden über ihren Dateinamen referenziert
 * ("photo": "photos/abc.jpg") und liegen in der ZIP-Datei daneben (siehe [BackupArchive]).
 * Beim Einlesen steht in photoPath zunächst dieser Eintragsname, nicht ein echter Pfad.
 */
object Backup {
    private const val FORMAT = 2
    const val PHOTO_FOLDER = "photos/"

    /** Eintragsname eines Fotos in der ZIP-Datei. */
    fun photoEntry(path: String?): Any = path?.let { PHOTO_FOLDER + java.io.File(it).name } ?: JSONObject.NULL

    fun toJson(recipes: List<RecipeWithIngredients>, bottles: List<Bottle>): String {
        val root = JSONObject()
        root.put("app", "TresenWerk")
        root.put("format", FORMAT)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("recipes", JSONArray().apply {
            recipes.forEach { rw ->
                val r = rw.recipe
                put(JSONObject().apply {
                    put("name", r.name)
                    put("category", r.category)
                    put("glass", r.glass)
                    put("ice", r.ice)
                    put("method", r.method)
                    put("garnish", r.garnish)
                    put("instructions", r.instructions)
                    put("notes", r.notes)
                    put("tags", r.tags)
                    put("favorite", r.isFavorite)
                    put("photo", photoEntry(r.photoPath))
                    put("createdAt", r.createdAt)
                    put("updatedAt", r.updatedAt)
                    put("ingredients", JSONArray().apply {
                        rw.sortedIngredients().forEach { i ->
                            put(JSONObject().apply {
                                put("name", i.name)
                                put("amount", i.amount ?: JSONObject.NULL)
                                put("unit", i.unit)
                            })
                        }
                    })
                })
            }
        })
        root.put("bottles", JSONArray().apply {
            bottles.forEach { b ->
                put(JSONObject().apply {
                    put("name", b.name)
                    put("brand", b.brand)
                    put("category", b.category)
                    put("sizeMl", b.sizeMl)
                    put("fillPercent", b.fillPercent)
                    put("bottleCount", b.bottleCount)
                    put("abv", b.abv ?: JSONObject.NULL)
                    put("labelText", b.labelText)
                    put("notes", b.notes)
                    put("updatedAt", b.updatedAt)
                    put("barcode", b.barcode ?: JSONObject.NULL)
                    put("photo", photoEntry(b.photoPath))
                    put("price", b.price ?: JSONObject.NULL)
                })
            }
        })
        return root.toString(2)
    }

    fun fromJson(text: String): Pair<List<RecipeWithIngredients>, List<Bottle>> {
        val root = JSONObject(text)
        require(root.optString("app") == "TresenWerk") { "Keine TresenWerk-Sicherung" }
        val now = System.currentTimeMillis()
        val recipes = root.optJSONArray("recipes").objects().map { o ->
            val recipe = Recipe(
                name = o.getString("name").take(MAX_SHORT),
                category = o.text("category"),
                glass = o.text("glass"),
                ice = o.text("ice"),
                method = o.text("method"),
                garnish = o.text("garnish"),
                instructions = o.text("instructions", MAX_LONG),
                notes = o.text("notes", MAX_LONG),
                tags = o.text("tags", MAX_LONG),
                isFavorite = o.optBoolean("favorite"),
                photoPath = o.photo(),
                createdAt = o.optLong("createdAt", now),
                updatedAt = o.optLong("updatedAt", now),
            )
            val ingredients = o.optJSONArray("ingredients").objects().mapIndexed { i, io ->
                Ingredient(
                    position = i,
                    name = io.getString("name").take(MAX_SHORT),
                    amount = io.decimal("amount", 0.0..100_000.0),
                    unit = io.text("unit", 20),
                )
            }
            RecipeWithIngredients(recipe, ingredients)
        }
        val bottles = root.optJSONArray("bottles").objects().map { o ->
            Bottle(
                name = o.getString("name").take(MAX_SHORT),
                brand = o.text("brand"),
                category = o.text("category"),
                sizeMl = o.optInt("sizeMl", 700).takeIf { it in 1..20_000 } ?: 700,
                fillPercent = o.optInt("fillPercent", 100).coerceIn(0, 100),
                bottleCount = o.optInt("bottleCount", 1).coerceIn(0, 999),
                abv = o.decimal("abv", 0.0..100.0),
                labelText = o.text("labelText", MAX_LONG),
                notes = o.text("notes", MAX_LONG),
                updatedAt = o.optLong("updatedAt", now),
                barcode = if (o.isNull("barcode")) null else o.optString("barcode").filter(Char::isDigit).take(14).ifBlank { null },
                price = o.decimal("price", 0.0..100_000.0),
                photoPath = o.photo(),
            )
        }
        return recipes to bottles
    }

    // Sinnvolle Bereiche – Werte aus einer (evtl. manipulierten) Sicherung werden darauf begrenzt
    private const val MAX_SHORT = 200
    private const val MAX_LONG = 10_000

    private fun JSONObject.text(key: String, max: Int = MAX_SHORT): String = optString(key).take(max)

    private fun JSONObject.decimal(key: String, range: ClosedFloatingPointRange<Double>): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() && it in range }

    private fun JSONObject.photo(): String? = if (isNull("photo")) null else optString("photo").ifBlank { null }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
