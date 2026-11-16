package de.tresenwerk.app.scan

import de.tresenwerk.app.data.Bottle
import org.junit.Assert.assertEquals
import org.junit.Test

/** Echte Scans vom Fairphone (16.11.2026). */
class RealScanTest {
    private val stored = listOf(
        Bottle(id = 1, name = "CHARTREUSE", labelText = "CHARTREUSE\nCHARTREUSE DIFFUSIO I-Aiguenoie\n383801 Entre-deux-Guiers FRANCE\nLIQUEUR\n55%\n70 cl\n1605\nLIQUEUR FABRIQUÉE\nPAR LES PERES\nProduit de France\nLith.Altier. Grenolilk\nCHARTREUX\nDeperé 1-7 - 69."),
        Bottle(id = 2, name = "Friedrichs", brand = "Friedrich Schwarze GmbH", barcode = "4007675887000", labelText = "riedtiks\nDRY GIN\n45% vol | 700ml\nlocal distilling tradition"),
    )

    @Test
    fun secondFriedrichsScanFindsFirst() {
        val lines = listOf("hiedil", "DRY GIN", "45% vol | 700ml", "local distilling tradition")
        assertEquals(2L, BottleMatcher.byLabel(lines, stored)?.id)
        assertEquals(null, LabelParser.parse(lines, uncertain = setOf("hiedil")).suggestedName)
    }

    @Test
    fun chartreuseNameStillFound() {
        val info = LabelParser.parse(listOf("CHARTREUSE", "CHARTREUSE DIFFUSIO I-Aiguenoie", "LIQUEUR", "55%", "70 cl"))
        assertEquals("CHARTREUSE", info.suggestedName)
        assertEquals("Likör", info.category)
        assertEquals(700, info.sizeMl)
    }
}
