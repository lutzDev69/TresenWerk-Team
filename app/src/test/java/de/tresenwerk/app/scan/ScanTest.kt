package de.tresenwerk.app.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanTest {
    @Test
    fun readsTypicalLabel() {
        val info = LabelParser.parse(listOf("HENDRICK'S", "GIN", "Distilled in Scotland", "41,4% vol", "70cl"))
        assertEquals("HENDRICK'S", info.suggestedName)
        assertEquals(700, info.sizeMl)
        assertEquals(41.4, info.abv!!, 0.001)
    }

    @Test
    fun readsVolumeVariants() {
        assertEquals(700, LabelParser.parse(listOf("0,7 L")).sizeMl)
        assertEquals(1000, LabelParser.parse(listOf("1 Liter")).sizeMl)
        assertEquals(750, LabelParser.parse(listOf("750 ml")).sizeMl)
        assertNull(LabelParser.parse(listOf("Lemon Twist")).sizeMl)
    }

    @Test
    fun ignoresHundredPercentAgave() {
        val info = LabelParser.parse(listOf("100% Agave", "Alc. 38% Vol."))
        assertEquals(38.0, info.abv!!, 0.001)
    }

    @Test
    fun fillLevelFromLines() {
        assertEquals(100, FillLevel.percent(bottom = 0.9f, full = 0.3f, level = 0.3f))
        assertEquals(50, FillLevel.percent(bottom = 0.9f, full = 0.3f, level = 0.6f))
        assertEquals(0, FillLevel.percent(bottom = 0.9f, full = 0.3f, level = 0.95f))
        assertEquals(100, FillLevel.percent(bottom = 0.9f, full = 0.3f, level = 0.1f))
        assertEquals(350, FillLevel.remainingMl(700, 50))
    }
}
