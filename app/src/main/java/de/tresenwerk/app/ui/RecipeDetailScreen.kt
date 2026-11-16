package de.tresenwerk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.data.displayName
import de.tresenwerk.app.data.tagList
import de.tresenwerk.app.domain.BatchCalculator
import de.tresenwerk.app.domain.Measure
import de.tresenwerk.app.domain.Stock
import de.tresenwerk.app.domain.formatAmount
import de.tresenwerk.app.domain.formatIngredient
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    data: RecipeWithIngredients,
    bottles: List<Bottle>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    val r = data.recipe
    var confirmDelete by remember { mutableStateOf(false) }
    var portions by rememberSaveable(r.id) { mutableIntStateOf(1) }
    var dilution by rememberSaveable(r.id) { mutableFloatStateOf(0f) }
    var unitName by rememberSaveable { mutableStateOf(Measure.ML.name) }
    val unit = Measure.valueOf(unitName)
    val batch = remember(data, portions, dilution, unit) {
        BatchCalculator.scale(data.ingredients, portions, dilution.toDouble(), unit)
    }

    if (confirmDelete) ConfirmDeleteDialog(r.name, onConfirm = onDelete, onDismiss = { confirmDelete = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (r.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorit",
                            tint = if (r.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Teilen") }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Bearbeiten") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Löschen") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (r.photoPath != null) {
                AsyncImage(
                    model = File(r.photoPath),
                    contentDescription = r.name,
                    modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            val facts = listOf("Methode" to r.method, "Glas" to r.glass, "Eis" to r.ice, "Garnitur" to r.garnish, "Kategorie" to r.category)
                .filter { it.second.isNotBlank() }
            if (facts.isNotEmpty()) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        facts.forEach { (label, value) ->
                            Row {
                                Text(label, Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            if (r.tagList().isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.tagList().forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
                }
            }

            SectionTitle(if (portions == 1) "Zutaten" else "Zutaten für $portions Drinks")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                batch.lines.forEach { line ->
                    val bottle = Stock.find(line.name, bottles)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatIngredient(line.amount, line.unit, line.name),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (bottle != null) {
                            Text(
                                "${bottle.displayName().take(18)} ${bottle.fillPercent} %",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bottle.fillPercent <= 20) LowStock else MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
                if (data.ingredients.isEmpty()) Text("Keine Zutaten eingetragen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            BatchCard(
                portions = portions,
                onPortions = { portions = it.coerceIn(1, 500) },
                dilution = dilution,
                onDilution = { dilution = it },
                unit = unit,
                onUnit = { unitName = it.name },
                total = batch.total,
                water = batch.water,
            )

            if (r.instructions.isNotBlank()) {
                SectionTitle("Zubereitung")
                Text(r.instructions, style = MaterialTheme.typography.bodyLarge)
            }
            if (r.notes.isNotBlank()) {
                SectionTitle("Notizen")
                Text(r.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchCard(
    portions: Int,
    onPortions: (Int) -> Unit,
    dilution: Float,
    onDilution: (Float) -> Unit,
    unit: Measure,
    onUnit: (Measure) -> Unit,
    total: Double,
    water: Double,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Batch & Umrechnung", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Portionen", Modifier.weight(1f))
                FilledTonalIconButton(onClick = { onPortions(portions - 1) }) { Text("−") }
                Text("$portions", Modifier.width(40.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                FilledTonalIconButton(onClick = { onPortions(portions + 1) }) { Text("+") }
                FilledTonalIconButton(onClick = { onPortions(portions + 10) }) { Text("+10") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Measure.volume.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = unit == m,
                        onClick = { onUnit(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, Measure.volume.size),
                    ) { Text(m.label) }
                }
            }
            Text("Verdünnung (Wasser für Pre-Batch): ${dilution.toInt()} %")
            Slider(value = dilution, onValueChange = onDilution, valueRange = 0f..40f, steps = 7)
            if (total > 0) {
                val summary = buildString {
                    append("Flüssigkeit: ${formatAmount(total)} ${unit.label}")
                    if (water > 0) append("  +  Wasser: ${formatAmount(water)} ${unit.label}  =  ${formatAmount(total + water)} ${unit.label}")
                }
                Text(summary, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
