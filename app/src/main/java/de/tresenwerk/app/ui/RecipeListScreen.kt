package de.tresenwerk.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tresenwerk.app.data.Recipe
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.data.tagList
import de.tresenwerk.app.domain.RecipeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    recipes: List<RecipeWithIngredients>,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onToggleFavorite: (Recipe) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var method by rememberSaveable { mutableStateOf<String?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }

    val methods = remember(recipes) { recipes.map { it.recipe.method.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted() }
    val tags = remember(recipes) { recipes.flatMap { it.recipe.tagList() }.distinctBy { it.lowercase() }.sorted() }
    val shown = remember(recipes, query, favoritesOnly, method, tag) {
        RecipeFilter.apply(recipes, query, favoritesOnly, method, tag)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Rezepte (${recipes.size})") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, contentDescription = "Neues Rezept") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Suche: Name, Zutat, Tag …") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Suche leeren") } }
                } else null,
                singleLine = true,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("★ Favoriten") })
                }
                items(methods) { m ->
                    FilterChip(selected = method == m, onClick = { method = if (method == m) null else m }, label = { Text(m) })
                }
                items(tags) { t ->
                    FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text("#$t") })
                }
            }
            if (shown.isEmpty()) {
                EmptyHint(
                    if (recipes.isEmpty()) "Noch keine Rezepte.\nTippe auf + oder lade unter „Daten“ Beispielrezepte."
                    else "Nichts gefunden. Suche oder Filter ändern.",
                    Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(shown, key = { it.recipe.id }) { r ->
                        RecipeRow(r, onClick = { onOpen(r.recipe.id) }, onToggleFavorite = { onToggleFavorite(r.recipe) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeRow(data: RecipeWithIngredients, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val r = data.recipe
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(r.photoPath, r.name)
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val facts = listOf(r.method, r.glass).filter { it.isNotBlank() }.joinToString(" · ")
            if (facts.isNotEmpty()) {
                Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text(
                data.sortedIngredients().joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (r.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (r.isFavorite) "Favorit entfernen" else "Als Favorit merken",
                tint = if (r.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
