package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Inventurliste des Flaschenbestands als CSV für den Steuerberater.
 * Format für deutsches Excel: Semikolon als Trenner, Dezimalkomma, UTF-8 mit BOM, Windows-Zeilenenden.
 * Angebrochene Flaschen werden anteilig nach Füllstand bewertet; volle und angebrochene stehen in eigenen
 * Spalten, damit sich auch andere Bewertungsregeln nachrechnen lassen.
 */
object InventoryCsv {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    val header = listOf(
        "Sorte", "Marke", "Produkt", "Flaschengröße (ml)", "Barcode",
        "Volle Flaschen", "Angebrochene Flasche Füllstand (%)", "Bestand (Flaschen)", "Bestand (Liter)",
        "Einkaufspreis netto pro Flasche (€)", "Warenwert netto (€)", "Hinweis",
    )

    data class Row(val bottle: Bottle, val fullBottles: Int, val openPercent: Int, val stockBottles: Double, val value: Double?)

    fun rows(bottles: List<Bottle>): List<Row> =
        bottles
            .sortedWith(compareBy({ it.category.ifBlank { "~" }.lowercase() }, { it.brand.lowercase() }, { it.name.lowercase() }))
            .map { b ->
                val full = (b.bottleCount - 1).coerceAtLeast(0)
                val open = if (b.bottleCount >= 1) b.fillPercent.coerceIn(0, 100) else 0
                val stock = full + open / 100.0
                Row(b, full, open, stock, b.price?.let { it * stock })
            }

    fun build(bottles: List<Bottle>, date: LocalDate): String {
        val rows = rows(bottles)
        val lines = mutableListOf<List<String>>()
        lines += listOf("Inventur Flaschenbestand", "Stichtag", date.format(dateFormat))
        lines += emptyList<String>()
        lines += header
        rows.forEach { r ->
            val b = r.bottle
            val hint = when {
                b.price == null && r.stockBottles > 0 -> "Preis fehlt"
                else -> ""
            }
            lines += listOf(
                b.category,
                b.brand,
                b.name,
                b.sizeMl.toString(),
                b.barcode.orEmpty(),
                r.fullBottles.toString(),
                r.openPercent.toString(),
                number(r.stockBottles, 2),
                number(r.stockBottles * b.sizeMl / 1000.0, 3),
                b.price?.let { number(it, 2) }.orEmpty(),
                r.value?.let { number(it, 2) }.orEmpty(),
                hint,
            )
        }
        lines += emptyList<String>()
        val total = rows.sumOf { it.value ?: 0.0 }
        lines += listOf("Summe Warenwert netto (€)", "", "", "", "", "", "", "", "", "", number(total, 2), "")
        val missing = rows.count { it.bottle.price == null && it.stockBottles > 0 }
        if (missing > 0) lines += listOf("Achtung: $missing Position(en) ohne Einkaufspreis sind nicht in der Summe enthalten.")
        return "﻿" + lines.joinToString("\r\n") { it.joinToString(";") { field -> escape(field) } } + "\r\n"
    }

    fun fileName(date: LocalDate): String = "tresenwerk-inventur-$date.csv"

    /** Dezimalkomma, ohne Tausenderpunkte (sonst rechnet Excel falsch). */
    private fun number(value: Double, decimals: Int): String =
        String.format(Locale.GERMANY, "%.${decimals}f", value)

    /** Felder mit Semikolon, Anführungszeichen oder Zeilenumbruch in Anführungszeichen setzen. */
    private fun escape(field: String): String =
        if (field.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
