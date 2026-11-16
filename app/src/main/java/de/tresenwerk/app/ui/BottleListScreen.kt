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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.displayName
import de.tresenwerk.app.domain.ShoppingList
import de.tresenwerk.app.domain.formatAmount
import de.tresenwerk.app.domain.formatEuro
import android.content.Intent
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import de.tresenwerk.app.scan.FillLevel

const val LOW_STOCK_PERCENT = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleListScreen(
    bottles: List<Bottle>,
    onOpen: (Long) -> Unit,
    onScan: () -> Unit,
    onAdd: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var lowOnly by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember(bottles) { bottles.map { it.category.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted() }
    val shown = remember(bottles, query, lowOnly, category) {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        bottles.filter { b ->
            val hay = "${b.name} ${b.brand} ${b.category} ${b.notes} ${b.barcode ?: ""}".lowercase()
            (!lowOnly || b.fillPercent <= LOW_STOCK_PERCENT) &&
                (category == null || b.category.equals(category, ignoreCase = true)) &&
                terms.all { it in hay }
        }
    }
    val lowCount = bottles.count { it.fillPercent <= LOW_STOCK_PERCENT }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bar-Bestand (${bottles.size})") },
                actions = {
                    TextButton(onClick = {
                        val text = ShoppingList.format(bottles, LOW_STOCK_PERCENT, LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Einkaufsliste Bar")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Einkaufsliste teilen"))
                    }) { Text("Einkaufsliste") }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Flasche manuell anlegen") }
                ExtendedFloatingActionButton(onClick = onScan, text = { Text("Etikett scannen") }, icon = { Text("📷") })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Flasche suchen …") },
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
                    FilterChip(
                        selected = lowOnly,
                        onClick = { lowOnly = !lowOnly },
                        label = { Text("Nachkaufen ($lowCount)") },
                    )
                }
                items(categories) { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                }
            }
            if (shown.isEmpty()) {
                EmptyHint(
                    if (bottles.isEmpty()) "Noch keine Flaschen.\n„Etikett scannen“ fotografiert eine Flasche und liest Name, Größe und Alkoholgehalt aus."
                    else "Keine passenden Flaschen.",
                    Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 150.dp)) {
                    items(shown, key = { it.id }) { b -> BottleRow(b) { onOpen(b.id) } }
                }
            }
        }
    }
}

@Composable
private fun BottleRow(b: Bottle, onClick: () -> Unit) {
    val low = b.fillPercent <= LOW_STOCK_PERCENT
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(b.photoPath, b.displayName())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(b.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val facts = listOfNotNull(
                b.category.takeIf { it.isNotBlank() },
                "${b.sizeMl} ml",
                b.abv?.let { "${formatAmount(it)} % vol" },
                b.price?.let { formatEuro(it) },
            ).joinToString(" · ")
            Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { b.fillPercent / 100f },
                    modifier = Modifier.weight(1f),
                    color = if (low) LowStock else MaterialTheme.colorScheme.secondary,
                )
                Text(
                    "${b.fillPercent} % · ~${FillLevel.remainingMl(b.sizeMl, b.fillPercent)} ml",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (low) LowStock else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
