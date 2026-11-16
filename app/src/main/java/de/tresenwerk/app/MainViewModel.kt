package de.tresenwerk.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tresenwerk.app.data.AppDatabase
import de.tresenwerk.app.data.Backup
import de.tresenwerk.app.data.BackupArchive
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.Ingredient
import de.tresenwerk.app.data.Recipe
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.data.SampleRecipes
import de.tresenwerk.app.domain.InventoryCsv
import de.tresenwerk.app.domain.formatEuro
import de.tresenwerk.app.util.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/** Bindeglied zwischen Bildschirmen und Datenbank. */
class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val recipeDao = db.recipes()
    private val bottleDao = db.bottles()

    val recipes: StateFlow<List<RecipeWithIngredients>?> =
        recipeDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bottles: StateFlow<List<Bottle>?> =
        bottleDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val used = recipeDao.getAll().mapNotNull { it.recipe.photoPath } + bottleDao.getAll().mapNotNull { it.photoPath }
            withContext(Dispatchers.IO) { Photos.cleanup(app, used.toSet()) }
        }
    }

    fun observeRecipe(id: Long): Flow<RecipeWithIngredients?> = recipeDao.observe(id)
    suspend fun loadRecipe(id: Long) = recipeDao.get(id)
    suspend fun loadBottle(id: Long) = bottleDao.get(id)

    fun saveRecipe(recipe: Recipe, ingredients: List<Ingredient>, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val oldPhoto = if (recipe.id != 0L) recipeDao.get(recipe.id)?.recipe?.photoPath else null
            val id = recipeDao.save(recipe.copy(updatedAt = System.currentTimeMillis()), ingredients)
            if (oldPhoto != null && oldPhoto != recipe.photoPath) Photos.delete(oldPhoto)
            onSaved(id)
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            recipeDao.delete(recipe.id)
            Photos.delete(recipe.photoPath)
        }
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch { recipeDao.setFavorite(recipe.id, !recipe.isFavorite) }
    }

    fun saveBottle(bottle: Bottle, onSaved: () -> Unit) {
        viewModelScope.launch {
            val b = bottle.copy(updatedAt = System.currentTimeMillis())
            if (b.id == 0L) {
                bottleDao.insert(b)
            } else {
                val oldPhoto = bottleDao.get(b.id)?.photoPath
                bottleDao.update(b)
                if (oldPhoto != null && oldPhoto != b.photoPath) Photos.delete(oldPhoto)
            }
            onSaved()
        }
    }

    fun deleteBottle(bottle: Bottle) {
        viewModelScope.launch {
            bottleDao.delete(bottle.id)
            Photos.delete(bottle.photoPath)
        }
    }

    /** Schreibt die ZIP-Sicherung (Rezepte, Flaschen und Fotos). */
    fun export(target: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val recipes = recipeDao.getAll()
                val bottles = bottleDao.getAll()
                val json = Backup.toJson(recipes, bottles)
                val photos = (recipes.mapNotNull { it.recipe.photoPath } + bottles.mapNotNull { it.photoPath }).map(::File)
                val photoCount = withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(target, "wt")?.use { BackupArchive.write(it, json, photos) }
                        ?: error("Datei lässt sich nicht schreiben")
                }
                "Gesichert: ${recipes.size} Rezepte, ${bottles.size} Flaschen, $photoCount Fotos."
            }.getOrElse { "Export fehlgeschlagen: ${it.message}" }
            onResult(message)
        }
    }

    /** Schreibt die Inventurliste (CSV für den Steuerberater) zum Zeitpunkt [at]. */
    fun exportInventory(target: Uri, at: LocalDateTime, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val bottles = bottleDao.getAll()
                val csv = InventoryCsv.build(bottles, at)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(target, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: error("Datei lässt sich nicht schreiben")
                }
                val total = InventoryCsv.rows(bottles).sumOf { it.value ?: 0.0 }
                val missing = InventoryCsv.rows(bottles).count { it.bottle.price == null && it.stockBottles > 0 }
                "Inventur exportiert: ${bottles.size} Positionen, Warenwert netto ${formatEuro(total)}" +
                    (if (missing > 0) " ($missing ohne Preis)." else ".")
            }.getOrElse { "Inventur-Export fehlgeschlagen: ${it.message}" }
            onResult(message)
        }
    }

    /**
     * Fügt Rezepte/Flaschen aus einer Sicherung (ZIP mit Fotos oder ältere JSON-Datei) hinzu.
     * Gleichnamige Einträge werden übersprungen.
     */
    fun import(source: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val tempDir = File(app.cacheDir, "import_${System.currentTimeMillis()}")
            val message = runCatching {
                val content = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(source)?.use { BackupArchive.read(it, tempDir) }
                        ?: error("Datei lässt sich nicht lesen")
                }
                val (recipes, bottles) = Backup.fromJson(content.json)
                val result = addAll(recipes, bottles, content.photos)
                "Importiert: ${result.recipes} Rezepte, ${result.bottles} Flaschen, ${result.photos} Fotos" +
                    (if (result.skipped > 0) " (${result.skipped} schon vorhanden, übersprungen)." else ".")
            }.getOrElse { "Import fehlgeschlagen: ${it.message}" }
            withContext(Dispatchers.IO) { tempDir.deleteRecursively() }
            onResult(message)
        }
    }

    fun addSamples(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = addAll(SampleRecipes.all, emptyList(), emptyMap())
            onResult(if (result.recipes > 0) "${result.recipes} Beispielrezepte hinzugefügt." else "Beispielrezepte sind schon vorhanden (${result.skipped}).")
        }
    }

    private class ImportResult(val recipes: Int, val bottles: Int, val photos: Int, val skipped: Int)

    /** [photos]: Eintragsname im Archiv → entpackte Datei. In photoPath steht beim Import der Eintragsname. */
    private suspend fun addAll(
        recipes: List<RecipeWithIngredients>,
        bottles: List<Bottle>,
        photos: Map<String, File>,
    ): ImportResult {
        val existingRecipes = recipeDao.getAll().map { it.recipe.name.lowercase() }.toMutableSet()
        val existingBottles = bottleDao.getAll().map { (it.brand + "|" + it.name).lowercase() }.toMutableSet()
        var r = 0
        var b = 0
        var p = 0
        var skipped = 0

        suspend fun takePhoto(entry: String?): String? {
            val file = entry?.let { photos[it] } ?: return null
            return withContext(Dispatchers.IO) {
                val target = File(Photos.dir(app), "${UUID.randomUUID()}.jpg")
                runCatching { file.copyTo(target); target.absolutePath }.getOrNull()
            }?.also { p++ }
        }

        recipes.forEach { rw ->
            if (existingRecipes.add(rw.recipe.name.lowercase())) {
                recipeDao.save(rw.recipe.copy(id = 0, photoPath = takePhoto(rw.recipe.photoPath)), rw.sortedIngredients())
                r++
            } else skipped++
        }
        bottles.forEach { bottle ->
            if (existingBottles.add((bottle.brand + "|" + bottle.name).lowercase())) {
                bottleDao.insert(bottle.copy(id = 0, photoPath = takePhoto(bottle.photoPath)))
                b++
            } else skipped++
        }
        return ImportResult(r, b, p, skipped)
    }
}
