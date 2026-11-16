package de.tresenwerk.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.domain.RecipeText
import de.tresenwerk.app.ui.BottleEditScreen
import de.tresenwerk.app.ui.BottleListScreen
import de.tresenwerk.app.ui.DataScreen
import de.tresenwerk.app.ui.EmptyHint
import de.tresenwerk.app.ui.RecipeDetailScreen
import de.tresenwerk.app.ui.RecipeEditScreen
import de.tresenwerk.app.ui.RecipeListScreen
import de.tresenwerk.app.ui.TresenWerkTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TresenWerkTheme {
                Surface(Modifier.fillMaxSize()) { TresenWerkApp() }
            }
        }
    }
}

/** Alle Bildschirme der App. Die drei Hauptbereiche sind unten in der Leiste wählbar. */
private sealed interface Screen {
    data object Recipes : Screen
    data object Bar : Screen
    data object Data : Screen
    data class RecipeDetail(val id: Long) : Screen
    data class RecipeEdit(val id: Long?) : Screen
    data class BottleEdit(val id: Long?, val scan: Boolean) : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Recipes -> "recipes"
    Screen.Bar -> "bar"
    Screen.Data -> "data"
    is Screen.RecipeDetail -> "rd:$id"
    is Screen.RecipeEdit -> "re:${id ?: ""}"
    is Screen.BottleEdit -> "be:${id ?: ""}:$scan"
}

private fun decodeScreen(s: String): Screen {
    val p = s.split(':')
    return when (p[0]) {
        "bar" -> Screen.Bar
        "data" -> Screen.Data
        "rd" -> p.getOrNull(1)?.toLongOrNull()?.let { Screen.RecipeDetail(it) } ?: Screen.Recipes
        "re" -> Screen.RecipeEdit(p.getOrNull(1)?.toLongOrNull())
        "be" -> Screen.BottleEdit(p.getOrNull(1)?.toLongOrNull(), p.getOrNull(2).toBoolean())
        else -> Screen.Recipes
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf(*saved.map(::decodeScreen).toTypedArray()) },
)

/** Hilfsklasse: unterscheidet "lädt noch" (null) von "geladen, aber nicht vorhanden" (Loaded(null)). */
private class Loaded<T>(val value: T?)

@Composable
private fun TresenWerkApp(vm: MainViewModel = viewModel()) {
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Recipes) }
    val current = backStack.last()
    val context = LocalContext.current

    fun go(screen: Screen) = backStack.add(screen)
    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    fun replaceTop(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }
    BackHandler(enabled = backStack.size > 1) { back() }

    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val bottles by vm.bottles.collectAsStateWithLifecycle()

    val bottomBar: @Composable () -> Unit = {
        NavigationBar {
            listOf(
                Triple(Screen.Recipes, "Rezepte", Icons.AutoMirrored.Filled.List),
                Triple(Screen.Bar, "Bar", Icons.Default.ShoppingCart),
                Triple(Screen.Data, "Daten", Icons.Default.Settings),
            ).forEach { (screen, label, icon) ->
                NavigationBarItem(
                    selected = current == screen,
                    onClick = {
                        backStack.clear()
                        backStack.add(screen)
                    },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(label) },
                )
            }
        }
    }

    when (current) {
        Screen.Recipes -> {
            val list = recipes
            if (list == null) Loading() else RecipeListScreen(
                recipes = list,
                bottles = bottles ?: emptyList(),
                onOpen = { go(Screen.RecipeDetail(it)) },
                onNew = { go(Screen.RecipeEdit(null)) },
                onToggleFavorite = vm::toggleFavorite,
                bottomBar = bottomBar,
            )
        }

        Screen.Bar -> {
            val list = bottles
            if (list == null) Loading() else BottleListScreen(
                bottles = list,
                onOpen = { go(Screen.BottleEdit(it, scan = false)) },
                onScan = { go(Screen.BottleEdit(null, scan = true)) },
                onAdd = { go(Screen.BottleEdit(null, scan = false)) },
                bottomBar = bottomBar,
            )
        }

        Screen.Data -> {
            var message by remember { mutableStateOf<String?>(null) }
            DataScreen(
                recipeCount = recipes?.size ?: 0,
                bottleCount = bottles?.size ?: 0,
                message = message,
                onExport = { vm.export(it) { msg -> message = msg } },
                onImport = { vm.import(it) { msg -> message = msg } },
                onAddSamples = { vm.addSamples { msg -> message = msg } },
                onExportInventory = { uri, date -> vm.exportInventory(uri, date) { msg -> message = msg } },
                bottomBar = bottomBar,
            )
        }

        is Screen.RecipeDetail -> {
            val flow = remember(current.id) { vm.observeRecipe(current.id).map { Loaded(it) } }
            val loaded by flow.collectAsStateWithLifecycle(initialValue = null)
            val data = loaded?.value
            when {
                loaded == null -> Loading()
                data == null -> EmptyHint("Rezept nicht gefunden.", Modifier.fillMaxSize())
                else -> RecipeDetailScreen(
                    data = data,
                    bottles = bottles ?: emptyList(),
                    onBack = ::back,
                    onEdit = { go(Screen.RecipeEdit(current.id)) },
                    onDelete = {
                        back()
                        vm.deleteRecipe(data.recipe)
                    },
                    onToggleFavorite = { vm.toggleFavorite(data.recipe) },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, data.recipe.name)
                            putExtra(Intent.EXTRA_TEXT, RecipeText.format(data))
                        }
                        context.startActivity(Intent.createChooser(send, "Rezept teilen"))
                    },
                )
            }
        }

        is Screen.RecipeEdit -> {
            val loaded by produceState<Loaded<RecipeWithIngredients>?>(null, current.id) {
                value = Loaded(current.id?.let { vm.loadRecipe(it) })
            }
            val l = loaded
            if (l == null) Loading() else RecipeEditScreen(
                initial = l.value,
                onSave = { recipe, ingredients ->
                    vm.saveRecipe(recipe, ingredients) { id ->
                        if (current.id == null) replaceTop(Screen.RecipeDetail(id)) else back()
                    }
                },
                onCancel = ::back,
            )
        }

        is Screen.BottleEdit -> {
            val loaded by produceState<Loaded<Bottle>?>(null, current.id) {
                value = Loaded(current.id?.let { vm.loadBottle(it) })
            }
            val l = loaded
            if (l == null) Loading() else BottleEditScreen(
                initial = l.value,
                allBottles = bottles ?: emptyList(),
                startWithCamera = current.scan,
                onSave = { vm.saveBottle(it) { back() } },
                onDelete = {
                    back()
                    vm.deleteBottle(it)
                },
                onCancel = ::back,
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
