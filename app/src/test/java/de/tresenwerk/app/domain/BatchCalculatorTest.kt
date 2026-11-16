package de.tresenwerk.app.domain

import de.tresenwerk.app.data.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchCalculatorTest {
    private val daiquiri = listOf(
        Ingredient(position = 0, name = "Rum", amount = 60.0, unit = "ML"),
        Ingredient(position = 1, name = "Limette", amount = 2.5, unit = "CL"),
        Ingredient(position = 2, name = "Angostura", amount = 2.0, unit = "DASH"),
        Ingredient(position = 3, name = "Soda", amount = null, unit = "TOP"),
    )

    @Test
    fun scalesAndConvertsToMl() {
        val r = BatchCalculator.scale(daiquiri, portions = 10, dilutionPercent = 20.0, displayUnit = Measure.ML)
        assertEquals(600.0, r.lines[0].amount!!, 0.001)
        assertEquals(250.0, r.lines[1].amount!!, 0.001)
        assertEquals(Measure.ML, r.lines[1].unit)
        // Dash wird nur vervielfacht, nicht umgerechnet
        assertEquals(20.0, r.lines[2].amount!!, 0.001)
        assertEquals(Measure.DASH, r.lines[2].unit)
        assertNull(r.lines[3].amount)
        assertEquals(850.0, r.total, 0.001)
        assertEquals(170.0, r.water, 0.001)
    }

    @Test
    fun convertsToOunces() {
        val r = BatchCalculator.scale(daiquiri, portions = 1, dilutionPercent = 0.0, displayUnit = Measure.OZ)
        assertEquals(2.03, r.lines[0].amount!!, 0.01)
        assertEquals(0.0, r.water, 0.0)
    }

    @Test
    fun formatsGermanNumbers() {
        assertEquals("60", formatAmount(60.0))
        assertEquals("22,5", formatAmount(22.5))
        assertEquals("0,33", formatAmount(1.0 / 3))
        assertEquals("2 Dash Angostura", formatIngredient(2.0, Measure.DASH, "Angostura"))
        assertEquals("Soda (auffüllen)", formatIngredient(null, Measure.TOP, "Soda"))
    }
}
