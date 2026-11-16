package de.tresenwerk.app.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProductInfo(
    val name: String?,
    val brand: String?,
    val sizeMl: Int?,
    val abv: Double?,
    val category: String?,
)

/**
 * Schlägt einen Barcode in der freien Produktdatenbank Open Food Facts nach.
 * Gesendet wird ausschließlich die Barcode-Nummer.
 */
object ProductLookup {
    private const val FIELDS = "product_name,product_name_de,generic_name,brands,quantity,categories_tags,nutriments"

    sealed interface Result {
        data class Found(val info: ProductInfo) : Result
        data object NotFound : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun lookup(barcode: String): Result = withContext(Dispatchers.IO) {
        if (!barcode.all { it.isDigit() } || barcode.length !in 8..14) return@withContext Result.Failed("Ungültiger Barcode")
        val conn = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$FIELDS")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "TresenWerk/0.2 (Android)")
            when (val code = conn.responseCode) {
                200 -> parse(conn.inputStream.use { it.readBytes().decodeToString() })
                    ?.let { Result.Found(it) } ?: Result.NotFound
                404 -> Result.NotFound
                else -> Result.Failed("Server antwortet mit $code")
            }
        } catch (e: Exception) {
            Result.Failed("Keine Verbindung (${e.javaClass.simpleName})")
        } finally {
            conn.disconnect()
        }
    }

    /** Wertet die Antwort aus; null, wenn das Produkt unbekannt ist. */
    fun parse(json: String): ProductInfo? {
        val root = JSONObject(json)
        if (root.optInt("status") != 1) return null
        val p = root.optJSONObject("product") ?: return null
        val brand = p.optString("brands").split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() }
        val name = listOf("product_name_de", "product_name", "generic_name")
            .map { p.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() }
        val abv = p.optJSONObject("nutriments")?.let { n ->
            listOf("alcohol_value", "alcohol", "alcohol_100g")
                .map { n.optDouble(it) }
                .firstOrNull { !it.isNaN() && it in 0.5..96.0 }
        }
        val tags = p.optJSONArray("categories_tags")?.let { a -> (0 until a.length()).joinToString(" ") { a.optString(it) } } ?: ""
        val category = CategoryGuess.fromText(tags) ?: CategoryGuess.fromText(name ?: "")
        if (name == null && brand == null) return null
        return ProductInfo(
            name = name,
            brand = brand,
            sizeMl = LabelParser.parseVolume(p.optString("quantity")),
            abv = abv,
            category = category,
        )
    }
}
