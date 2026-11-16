package de.tresenwerk.app.domain

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Maßeinheiten an der Bar. [ml] ist der Umrechnungsfaktor in Milliliter,
 * null bei Einheiten, die sich nicht sinnvoll umrechnen lassen (Dash, Stück …).
 */
enum class Measure(val label: String, val longLabel: String, val ml: Double?, val approxMl: Double? = ml) {
    ML("ml", "Milliliter (ml)", 1.0),
    CL("cl", "Zentiliter (cl)", 10.0),
    OZ("oz", "Unze (oz)", 29.5735),
    // Für die Kostenrechnung genügen Näherungswerte
    DASH("Dash", "Dash", null, approxMl = 0.8),
    DROP("Tropfen", "Tropfen", null, approxMl = 0.05),
    BARSPOON("BL", "Barlöffel (BL)", null, approxMl = 5.0),
    PIECE("Stk.", "Stück", null),
    TOP("auff.", "Auffüllen (top)", null),
    NONE("", "ohne Einheit", null);

    companion object {
        val volume = listOf(ML, CL, OZ)

        /** Liest den in der Datenbank/Backup gespeicherten Wert, tolerant gegenüber Schreibweisen. */
        fun fromStored(value: String): Measure =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
                ?: NONE
    }
}

/** Zahl hübsch auf Deutsch: 60 → "60", 22.5 → "22,5", 1/3 → "0,33". */
fun formatAmount(value: Double): String {
    val rounded = (value * 100).roundToLong() / 100.0
    if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
    return String.format(Locale.GERMANY, "%.2f", rounded).trimEnd('0').trimEnd(',')
}

/** Eine Zeile wie "60 ml Weißer Rum", "2 Dash Angostura" oder "Soda (auffüllen)". */
fun formatIngredient(amount: Double?, unit: Measure, name: String): String = when {
    unit == Measure.TOP && amount == null -> "$name (auffüllen)"
    amount == null -> name
    unit == Measure.NONE -> "${formatAmount(amount)} $name"
    else -> "${formatAmount(amount)} ${unit.label} $name"
}

/** Geldbetrag auf Deutsch: 1.5 → "1,50 €". */
fun formatEuro(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)
