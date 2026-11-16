package de.tresenwerk.app.scan

import de.tresenwerk.app.data.Bottle
import kotlin.math.roundToInt

data class LabelInfo(
    val lines: List<String>,
    val suggestedName: String?,
    val sizeMl: Int?,
    val abv: Double?,
    val category: String?,
)

/**
 * Wertet den erkannten Etikett-Text aus. Die Zeilen kommen nach Schriftgröße sortiert
 * (größte zuerst) – die größte sicher erkannte Textzeile ist meist der Markenname.
 */
object LabelParser {
    private val volume = Regex(
        """(?<![\d.,])(\d+(?:[.,]\d+)?)\s*(ml|cl|ltr|liter|litre|l)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val alcohol = Regex("""(?<![\d.,])(\d{1,2}(?:[.,]\d{1,2})?)\s*%""")
    private val noise = listOf("product of", "produce of", "distilled", "imported", "since", "est.", "vol", "alc")

    /**
     * @param uncertain Zeilen, bei denen die Texterkennung unsicher war – sie werden nie als Name vorgeschlagen.
     */
    fun parse(linesBySize: List<String>, uncertain: Set<String> = emptySet()): LabelInfo {
        val lines = linesBySize.map { it.trim() }.filter { it.isNotEmpty() }
        val sizeMl = lines.firstNotNullOfOrNull { parseVolume(it) }
        val abv = lines.firstNotNullOfOrNull { line ->
            alcohol.find(line)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.5..96.0 }
        }
        // Nur die zwei größten Zeilen kommen als Name infrage – kleine Werbesprüche
        // ("local distilling tradition") sind nie der Name. Lieber leer lassen als Unsinn eintragen.
        val name = lines.take(2).firstOrNull { line ->
            line !in uncertain &&
                line.count { it.isLetter() } >= 3 &&
                !volume.containsMatchIn(line) &&
                !alcohol.containsMatchIn(line) &&
                noise.none { line.lowercase().contains(it) } &&
                !CategoryGuess.isOnlyCategory(line)   // "DRY GIN" ist die Sorte, nicht der Name
        }
        return LabelInfo(lines, name, sizeMl, abv, CategoryGuess.fromText(lines.joinToString(" ")))
    }

    /** "70cl", "0,7 l", "750 ml" … → Milliliter. */
    fun parseVolume(text: String): Int? {
        val match = volume.find(text) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val factor = when (match.groupValues[2].lowercase()) {
            "ml" -> 1.0
            "cl" -> 10.0
            else -> 1000.0
        }
        return (value * factor).roundToInt().takeIf { it in 20..5000 }
    }
}

/** Erkennt die Sorte aus Etikett-Text oder Datenbank-Kategorien. */
object CategoryGuess {
    // Reihenfolge zählt: "Gin Liqueur" ist ein Likör, "Amaretto" kein Gin.
    private val rules = listOf(
        "Wermut" to """vermouth|wermut|vermut""",
        "Bitter" to """bitters""",
        "Likör" to """liqueurs?|likör|liköre|liquore|creme de|crème de""",
        "Amaro" to """amaro|amari""",
        "Sirup" to """sirup|syrups?""",
        "Gin" to """gins?|genever""",
        "Rum" to """rums?|rhum|ron""",
        "Vodka" to """vodkas?|wodka""",
        "Whisk(e)y" to """whiske?ys?|whiskies|bourbons?|scotch""",
        "Tequila" to """tequilas?""",
        "Mezcal" to """mezcals?""",
        "Brandy" to """brandy|brandies|cognacs?|armagnacs?|calvados|pisco""",
    ).map { (cat, pattern) -> cat to Regex("""(?<![\p{L}])(?:$pattern)(?![\p{L}])""", RegexOption.IGNORE_CASE) }

    private val generic = Regex(
        """(?<![\p{L}])(dry|london|old|tom|premium|distilled|spiced|white|dark|aged|blanco|reposado|añejo|anejo|single|malt|blended|straight|kentucky|small|batch|the|and|de|di)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    /** true bei Zeilen wie "DRY GIN" oder "Single Malt Scotch Whisky", die nur die Sorte beschreiben. */
    fun isOnlyCategory(line: String): Boolean {
        if (fromText(line) == null) return false
        var rest = line.replace("en:", " ").replace('-', ' ')
        rules.forEach { rest = it.second.replace(rest, " ") }
        rest = generic.replace(rest, " ")
        return rest.count { it.isLetter() } < 3
    }

    fun fromText(text: String): String? {
        val t = text.replace("en:", " ").replace('-', ' ')
        return rules.firstOrNull { it.second.containsMatchIn(t) }?.first
    }
}

/**
 * Erkennt eine schon angelegte Flasche wieder – über den Barcode oder über den Etikett-Text.
 * Dadurch "lernt" die App: wer den Namen einmal korrigiert hat, bekommt ihn beim nächsten Scan richtig.
 */
object BottleMatcher {
    fun byBarcode(barcode: String?, bottles: List<Bottle>): Bottle? =
        barcode?.takeIf { it.isNotBlank() }?.let { code -> bottles.firstOrNull { it.barcode == code } }

    fun byLabel(lines: List<String>, bottles: List<Bottle>): Bottle? {
        val scanned = tokens(lines.joinToString(" "))
        if (scanned.size < 2) return null
        return bottles
            .filter { it.labelText.isNotBlank() }
            .map { it to similarity(scanned, tokens(it.labelText)) }
            .filter { it.second >= 0.4 }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Wörter, die auf vielen Etiketten stehen und daher nichts über die konkrete Flasche sagen. */
    private val common = setOf(
        "vol", "alc", "dry", "gin", "rum", "vodka", "whisky", "whiskey", "london", "distilled", "product",
        "of", "the", "and", "und", "premium", "aged", "years", "year", "jahre", "liqueur", "likör", "bottled",
    )

    private fun tokens(text: String): Set<String> =
        text.lowercase().split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 3 && it.any(Char::isLetter) && it !in common }
            .toSet()

    /** Gemeinsame Wörter geteilt durch alle Wörter (Jaccard, 0–1); mindestens 2 gemeinsame Wörter nötig. */
    private fun similarity(a: Set<String>, b: Set<String>): Double {
        val shared = a.intersect(b).size
        if (shared < 2) return 0.0
        return shared.toDouble() / a.union(b).size
    }
}
