package de.tresenwerk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.tresenwerk.app.data.Ingredient
import de.tresenwerk.app.data.Recipe
import de.tresenwerk.app.data.RecipeWithIngredients
import de.tresenwerk.app.domain.Measure
import de.tresenwerk.app.domain.formatAmount

private class IngredientDraft(name: String, amount: String, unit: Measure) {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    var unit by mutableStateOf(unit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    initial: RecipeWithIngredients?,
    onSave: (Recipe, List<Ingredient>) -> Unit,
    onCancel: () -> Unit,
) {
    val r = initial?.recipe
    var name by remember { mutableStateOf(r?.name ?: "") }
    var method by remember { mutableStateOf(r?.method ?: "") }
    var glass by remember { mutableStateOf(r?.glass ?: "") }
    var ice by remember { mutableStateOf(r?.ice ?: "") }
    var garnish by remember { mutableStateOf(r?.garnish ?: "") }
    var category by remember { mutableStateOf(r?.category ?: "") }
    var tags by remember { mutableStateOf(r?.tags ?: "") }
    var instructions by remember { mutableStateOf(r?.instructions ?: "") }
    var notes by remember { mutableStateOf(r?.notes ?: "") }
    var photoPath by remember { mutableStateOf(r?.photoPath) }
    var nameError by remember { mutableStateOf(false) }
    val ingredients = remember {
        mutableStateListOf<IngredientDraft>().apply {
            initial?.sortedIngredients()?.forEach {
                add(IngredientDraft(it.name, it.amount?.let(::formatAmount) ?: "", Measure.fromStored(it.unit)))
            }
            if (isEmpty()) add(IngredientDraft("", "", Measure.ML))
        }
    }
    val photoActions = rememberPhotoActions { photoPath = it }

    fun save() {
        if (name.isBlank()) {
            nameError = true
            return
        }
        val recipe = (r ?: Recipe(name = "")).copy(
            name = name.trim(),
            method = method.trim(),
            glass = glass.trim(),
            ice = ice.trim(),
            garnish = garnish.trim(),
            category = category.trim(),
            tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "),
            instructions = instructions.trim(),
            notes = notes.trim(),
            photoPath = photoPath,
        )
        val list = ingredients.filter { it.name.isNotBlank() }.map {
            Ingredient(
                name = it.name.trim(),
                amount = it.amount.trim().replace(',', '.').toDoubleOrNull(),
                unit = it.unit.name,
            )
        }
        onSave(recipe, list)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (r == null) "Neues Rezept" else "Rezept bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Abbrechen") }
                },
                actions = { TextButton(onClick = ::save) { Text("Speichern") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name *") },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Bitte einen Namen eingeben") }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            PhotoEditor(photoPath, photoActions, onRemove = { photoPath = null })

            SectionTitle("Zutaten")
            ingredients.forEachIndexed { index, ing ->
                key(ing) {
                    IngredientRow(ing, onRemove = { ingredients.removeAt(index) })
                }
            }
            OutlinedButton(onClick = { ingredients.add(IngredientDraft("", "", Measure.ML)) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Zutat hinzufügen")
            }

            SectionTitle("Service")
            FieldWithSuggestions("Methode", method, { method = it }, METHODS)
            FieldWithSuggestions("Glas", glass, { glass = it }, GLASSES)
            FieldWithSuggestions("Eis", ice, { ice = it }, ICES)
            OutlinedTextField(garnish, { garnish = it }, Modifier.fillMaxWidth(), label = { Text("Garnitur") }, singleLine = true)

            SectionTitle("Einordnung")
            OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("Kategorie / Basis") }, singleLine = true)
            OutlinedTextField(
                tags, { tags = it }, Modifier.fillMaxWidth(),
                label = { Text("Tags") },
                supportingText = { Text("Mit Komma trennen, z. B. Signature, Herbst, Karte 2026") },
                singleLine = true,
            )

            SectionTitle("Zubereitung & Notizen")
            OutlinedTextField(instructions, { instructions = it }, Modifier.fillMaxWidth(), label = { Text("Zubereitung") }, minLines = 3)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notizen (Kosten, Varianten, Herkunft …)") }, minLines = 2)
        }
    }
}

@Composable
private fun IngredientRow(ing: IngredientDraft, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = ing.amount,
            onValueChange = { ing.amount = it },
            modifier = Modifier.width(76.dp),
            label = { Text("Menge") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        UnitPicker(ing.unit) { ing.unit = it }
        OutlinedTextField(
            value = ing.name,
            onValueChange = { ing.name = it },
            modifier = Modifier.weight(1f),
            label = { Text("Zutat") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Zutat entfernen") }
    }
}

@Composable
private fun UnitPicker(unit: Measure, onPick: (Measure) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(unit.label.ifEmpty { "–" })
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Measure.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.longLabel) }, onClick = { onPick(m); open = false })
            }
        }
    }
}
