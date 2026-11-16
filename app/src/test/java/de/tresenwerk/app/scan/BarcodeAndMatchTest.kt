package de.tresenwerk.app.scan

import de.tresenwerk.app.data.Bottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeAndMatchTest {
    @Test
    fun parsesOpenFoodFactsAnswer() {
        val json = """{"code":"5010327755014","status":1,"product":{"product_name":"Gin","brands":"Hendrick's, William Grant",
            "quantity":"0.7l","categories_tags":["en:beverages","en:hard-liquors","en:gins"],
            "nutriments":{"alcohol":41.7,"alcohol_unit":"% vol","alcohol_value":41.7}}}"""
        val info = ProductLookup.parse(json)!!
        assertEquals("Gin", info.name)
        assertEquals("Hendrick's", info.brand)
        assertEquals(700, info.sizeMl)
        assertEquals(41.7, info.abv!!, 0.001)
        assertEquals("Gin", info.category)
    }

    @Test
    fun unknownProductIsNull() {
        assertNull(ProductLookup.parse("""{"code":"123","status":0,"status_verbose":"product not found"}"""))
    }

    @Test
    fun guessesCategory() {
        assertEquals("Gin", CategoryGuess.fromText("DRY GIN"))
        assertNull(CategoryGuess.fromText("Ginger Beer"))
        assertEquals("Likör", CategoryGuess.fromText("Sloe Gin Liqueur"))
        assertEquals("Whisk(e)y", CategoryGuess.fromText("Kentucky Straight Bourbon"))
        assertEquals("Rum", CategoryGuess.fromText("en:rums"))
        assertTrue(CategoryGuess.isOnlyCategory("DRY GIN"))
        assertTrue(CategoryGuess.isOnlyCategory("Single Malt Scotch Whisky"))
        assertFalse(CategoryGuess.isOnlyCategory("Hendrick's Gin"))
    }

    @Test
    fun uncertainOrCategoryLinesAreNotNames() {
        // So sah der Friedrichs-Scan aus: Schreibschrift-Logo falsch gelesen
        val lines = listOf("riedtiks", "DRY GIN", "45% vol | 700ml", "local distilling tradition")
        val info = LabelParser.parse(lines, uncertain = setOf("riedtiks"))
        assertNull(info.suggestedName)   // lieber leer als Werbespruch
        assertEquals("Gin", info.category)
        assertEquals(700, info.sizeMl)
        assertEquals(45.0, info.abv!!, 0.001)
    }

    @Test
    fun recognisesKnownBottleAgain() {
        val friedrichs = Bottle(
            id = 7, name = "Dry Gin", brand = "Friedrichs", barcode = "4260000000001",
            labelText = "riedtiks\nDRY GIN\n45% vol | 700ml\nlocal distilling tradition",
        )
        val other = Bottle(id = 8, name = "London Dry Gin", brand = "Tanqueray", labelText = "TANQUERAY\nLONDON DRY GIN\n47,3% vol")
        val bottles = listOf(friedrichs, other)

        assertEquals(7L, BottleMatcher.byBarcode("4260000000001", bottles)?.id)
        assertNull(BottleMatcher.byBarcode("999", bottles))
        // Zweiter Scan, leicht anders gelesen
        assertEquals(7L, BottleMatcher.byLabel(listOf("riedtiks", "DRY GIN", "45% vol", "local distilling traditon"), bottles)?.id)
        // Ein anderer Dry Gin darf nicht als Friedrichs erkannt werden
        assertNull(BottleMatcher.byLabel(listOf("MONKEY 47", "DRY GIN", "47% vol"), bottles))
    }
}
