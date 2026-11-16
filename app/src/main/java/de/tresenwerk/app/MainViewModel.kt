package de.tresenwerk.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tresenwerk.app.data.AppDatabase
import de.tresenwerk.app.data.Backup
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.Ingredient
import de.tresenwerk.app.data.Recipe
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.data.SampleRecipes
import de.tresenwerk.app.util.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun export(target: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val recipes = recipeDao.getAll()
                val bottles = bottleDao.getAll()
                val json = Backup.toJson(recipes, bottles)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.toByteArray()) }
                        ?: error("Datei lässt sich nicht schreiben")
                }
                "Gesichert: ${recipes.size} Rezepte, ${bottles.size} Flaschen."
            }.getOrElse { "Export fehlgeschlagen: ${it.message}" }
            onResult(message)
        }
    }

    /** Fügt Rezepte/Flaschen aus einer Sicherung hinzu. Gleichnamige Einträge werden übersprungen. */
    fun import(source: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
                        ?: error("Datei lässt sich nicht lesen")
                }
                val (recipes, bottles) = Backup.fromJson(text)
                val result = addAll(recipes, bottles)
                "Importiert: ${result.first} Rezepte, ${result.second} Flaschen" +
                    (if (result.third > 0) " (${result.third} schon vorhanden, übersprungen)." else ".")
            }.getOrElse { "Import fehlgeschlagen: ${it.message}" }
            onResult(message)
        }
    }

    fun addSamples(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val (added, _, skipped) = addAll(SampleRecipes.all, emptyList())
            onResult(if (added > 0) "$added Beispielrezepte hinzugefügt." else "Beispielrezepte sind schon vorhanden ($skipped).")
        }
    }

    private suspend fun addAll(recipes: List<RecipeWithIngredients>, bottles: List<Bottle>): Triple<Int, Int, Int> {
        val existingRecipes = recipeDao.getAll().map { it.recipe.name.lowercase() }.toMutableSet()
        val existingBottles = bottleDao.getAll().map { (it.brand + "|" + it.name).lowercase() }.toMutableSet()
        var r = 0
        var b = 0
        var skipped = 0
        recipes.forEach { rw ->
            if (existingRecipes.add(rw.recipe.name.lowercase())) {
                recipeDao.save(rw.recipe.copy(id = 0, photoPath = null), rw.sortedIngredients())
                r++
            } else skipped++
        }
        bottles.forEach { bottle ->
            if (existingBottles.add((bottle.brand + "|" + bottle.name).lowercase())) {
                bottleDao.insert(bottle.copy(id = 0, photoPath = null))
                b++
            } else skipped++
        }
        return Triple(r, b, skipped)
    }
}
