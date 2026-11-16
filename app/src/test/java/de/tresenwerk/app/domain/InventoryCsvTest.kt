package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Bottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InventoryCsvTest {
    private val bottles = listOf(
        Bottle(name = "Campari", category = "Bitter", sizeMl = 1000, fillPercent = 40, bottleCount = 3, price = 16.5),
        Bottle(name = "Dry Gin", brand = "Friedrichs", category = "Gin", sizeMl = 700, fillPercent = 50, bottleCount = 1, price = 30.0, barcode = "4007675887000"),
        Bottle(name = "Hausmarke; \"Spezial\"", category = "Rum", sizeMl = 700, fillPercent = 100, bottleCount = 1),
        Bottle(name = "Leer", category = "Gin", sizeMl = 700, fillPercent = 80, bottleCount = 0, price = 20.0),
    )
    private val csv = InventoryCsv.build(bottles, LocalDate.of(2026, 12, 31))
    private val lines = csv.removePrefix("﻿").split("\r\n")

    @Test
    fun excelFriendlyFormat() {
        assertTrue(csv.startsWith("﻿"))
        assertEquals("Inventur Flaschenbestand;Stichtag;31.12.2026", lines[0])
        assertTrue(lines[2].startsWith("Sorte;Marke;Produkt;Flaschengröße (ml)"))
    }

    @Test
    fun valuesPerBottle() {
        // Campari: 2 volle + 40 % = 2,4 Flaschen × 16,50 € = 39,60 €
        assertEquals("Bitter;;Campari;1000;;2;40;2,40;2,400;16,50;39,60;", lines[3])
        assertEquals("Gin;Friedrichs;Dry Gin;700;4007675887000;0;50;0,50;0,350;30,00;15,00;", lines[5])
        // Anzahl 0 → nichts auf Lager, Wert 0
        assertEquals("Gin;;Leer;700;;0;0;0,00;0,000;20,00;0,00;", lines[4])   // ohne Marke zuerst
        // Sonderzeichen werden maskiert, fehlender Preis markiert
        assertEquals("Rum;;\"Hausmarke; \"\"Spezial\"\"\";700;;0;100;1,00;0,700;;;Preis fehlt", lines[6])
    }

    @Test
    fun totalAndWarning() {
        assertTrue(lines.any { it == "Summe Warenwert netto (€);;;;;;;;;;54,60;" })
        assertTrue(lines.any { it.startsWith("Achtung: 1 Position(en) ohne Einkaufspreis") })
    }
}
