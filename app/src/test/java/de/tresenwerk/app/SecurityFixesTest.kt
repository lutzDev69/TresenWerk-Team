package de.tresenwerk.app

import de.tresenwerk.app.data.Backup
import de.tresenwerk.app.data.BackupArchive
import de.tresenwerk.app.domain.InventoryCsv
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.util.TooLargeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SecurityFixesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun csvFormulasAreDefused() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, InventoryCsv.escape("=HYPERLINK(\"x\")"))
        assertEquals("'+49 Gin", InventoryCsv.escape("+49 Gin"))
        assertEquals("'-Spezial", InventoryCsv.escape("-Spezial"))
        assertEquals("'@SUM(A1)", InventoryCsv.escape("@SUM(A1)"))
        assertEquals("Monkey 47", InventoryCsv.escape("Monkey 47"))
        val csv = InventoryCsv.build(listOf(Bottle(name = "=1+1", price = 10.0)), LocalDateTime.of(2026, 1, 1, 12, 0))
        assertTrue(csv.contains(";'=1+1;"))
    }

    private fun zipWith(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> entries.forEach { (n, b) -> zip.putNextEntry(ZipEntry(n)); zip.write(b); zip.closeEntry() } }
        return out.toByteArray()
    }

    @Test(expected = TooLargeException::class)
    fun oversizedPhotoIsRejected() {
        val json = Backup.toJson(emptyList(), emptyList()).toByteArray()
        val data = zipWith(BackupArchive.JSON_ENTRY to json, "photos/big.jpg" to ByteArray(5000))
        BackupArchive.read(ByteArrayInputStream(data), tmp.newFolder(), BackupArchive.Limits(maxPhotoBytes = 1000))
    }

    @Test(expected = TooLargeException::class)
    fun oversizedJsonIsRejected() {
        BackupArchive.read(ByteArrayInputStream(ByteArray(3000) { 'x'.code.toByte() }), tmp.newFolder(), BackupArchive.Limits(maxJsonBytes = 1000))
    }

    @Test(expected = TooLargeException::class)
    fun tooManyEntriesAreRejected() {
        val entries = (1..20).map { "photos/p$it.jpg" to ByteArray(1) }.toTypedArray()
        BackupArchive.read(ByteArrayInputStream(zipWith(*entries)), tmp.newFolder(), BackupArchive.Limits(maxEntries = 10))
    }

    @Test
    fun importedValuesAreClamped() {
        val json = """{"app":"TresenWerk","format":2,"recipes":[],"bottles":[
            {"name":"${"X".repeat(5000)}","sizeMl":0,"fillPercent":500,"bottleCount":-3,"abv":250,"price":-5,"barcode":"12ab34"}]}"""
        val b = Backup.fromJson(json).second.single()
        assertEquals(200, b.name.length)
        assertEquals(700, b.sizeMl)
        assertEquals(100, b.fillPercent)
        assertEquals(0, b.bottleCount)
        assertNull(b.abv)
        assertNull(b.price)
        assertEquals("1234", b.barcode)
    }
}
