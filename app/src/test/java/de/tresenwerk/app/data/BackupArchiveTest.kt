package de.tresenwerk.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTripKeepsPhotos() {
        val photoDir = tmp.newFolder("photos")
        val recipePhoto = File(photoDir, "1b2c3d4e-aaaa-bbbb-cccc-123456789abc.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val bottlePhoto = File(photoDir, "9f8e7d6c-aaaa-bbbb-cccc-123456789abc.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val recipes = listOf(
            RecipeWithIngredients(
                Recipe(id = 5, name = "Negroni", photoPath = recipePhoto.absolutePath),
                listOf(Ingredient(name = "Gin", amount = 30.0, unit = "ML")),
            ),
            RecipeWithIngredients(Recipe(id = 6, name = "Ohne Foto"), emptyList()),
        )
        val bottles = listOf(Bottle(id = 2, name = "Friedrichs", photoPath = bottlePhoto.absolutePath, barcode = "4007675887000"))

        val out = ByteArrayOutputStream()
        val count = BackupArchive.write(out, Backup.toJson(recipes, bottles), listOf(recipePhoto, bottlePhoto, File(photoDir, "fehlt.jpg")))
        assertEquals(2, count)

        val content = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder("import"))
        val (r, b) = Backup.fromJson(content.json)
        val entry = r.first { it.recipe.name == "Negroni" }.recipe.photoPath!!
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), content.photos.getValue(entry).readBytes())
        assertNull(r.first { it.recipe.name == "Ohne Foto" }.recipe.photoPath)
        assertArrayEquals(byteArrayOf(9, 8, 7), content.photos.getValue(b.single().photoPath!!).readBytes())
        assertEquals("4007675887000", b.single().barcode)
    }

    @Test
    fun oldJsonBackupStillReadable() {
        val json = Backup.toJson(listOf(RecipeWithIngredients(Recipe(name = "Daiquiri"), emptyList())), emptyList())
        val content = BackupArchive.read(ByteArrayInputStream(json.toByteArray()), tmp.newFolder("x"))
        assertTrue(content.photos.isEmpty())
        assertEquals("Daiquiri", Backup.fromJson(content.json).first.single().recipe.name)
    }

    @Test
    fun rejectsDangerousEntryNames() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchive.JSON_ENTRY)); zip.write(Backup.toJson(emptyList(), emptyList()).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("photos/../../evil.jpg")); zip.write(byteArrayOf(0)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("photos/script.sh")); zip.write(byteArrayOf(0)); zip.closeEntry()
        }
        val target = tmp.newFolder("safe")
        val content = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), target)
        assertTrue(content.photos.isEmpty())
        assertTrue(target.listFiles()!!.isEmpty())
        assertTrue(!File(tmp.root, "evil.jpg").exists())
    }
}
