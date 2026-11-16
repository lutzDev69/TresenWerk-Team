package de.tresenwerk.app.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.tresenwerk.app.data.Bottle
import de.tresenwerk.app.data.displayName
import de.tresenwerk.app.data.stockLabel
import de.tresenwerk.app.domain.formatAmount
import de.tresenwerk.app.scan.BarcodeReader
import de.tresenwerk.app.scan.BottleMatcher
import de.tresenwerk.app.scan.LabelParser
import de.tresenwerk.app.scan.LabelScanner
import de.tresenwerk.app.scan.ProductInfo
import de.tresenwerk.app.scan.ProductLookup
import de.tresenwerk.app.util.Photos
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottleEditScreen(
    initial: Bottle?,
    allBottles: List<Bottle>,
    startWithCamera: Boolean,
    onSave: (Bottle) -> Unit,
    onDelete: (Bottle) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Die Flasche, die gespeichert wird. Wechselt, wenn der Scan eine vorhandene Flasche wiedererkennt.
    var base by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var nameIsAuto by remember { mutableStateOf(initial == null) }   // Name stammt (noch) nicht vom Nutzer
    var brand by remember { mutableStateOf(initial?.brand ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var sizeText by remember { mutableStateOf((initial?.sizeMl ?: 700).toString()) }
    var abvText by remember { mutableStateOf(initial?.abv?.let(::formatAmount) ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var priceText by remember { mutableStateOf(initial?.price?.let(::formatAmount) ?: "") }
    var fill by remember { mutableFloatStateOf((initial?.fillPercent ?: 100).toFloat()) }
    var count by remember { mutableIntStateOf(initial?.bottleCount ?: 1) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var labelText by remember { mutableStateOf(initial?.labelText ?: "") }
    var photoPath by remember { mutableStateOf(initial?.photoPath) }
    var nameError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showFillDialog by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf<String?>(null) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var lookupMessage by remember { mutableStateOf<String?>(null) }
    var detected by remember { mutableStateOf<List<String>>(emptyList()) }
    var targetIsName by remember { mutableStateOf(true) }
    var match by remember { mutableStateOf<Bottle?>(null) }
    var matchDismissed by remember { mutableStateOf(false) }

    val others = allBottles.filter { it.id != base?.id }

    fun suggestMatch(found: Bottle?) {
        if (found != null && !matchDismissed && found.id != base?.id) match = found
    }

    fun applyProduct(info: ProductInfo) {
        if (info.name != null && (nameIsAuto || name.isBlank())) {
            name = info.name
            nameIsAuto = true
        }
        if (info.brand != null && (brand.isBlank() || nameIsAuto)) brand = info.brand
        info.sizeMl?.let { sizeText = it.toString() }
        info.abv?.let { abvText = formatAmount(it) }
        if (category.isBlank()) info.category?.let { category = it }
    }

    fun lookupBarcode(code: String) {
        val known = BottleMatcher.byBarcode(code, others)
        if (known != null) {
            suggestMatch(known)
            lookupMessage = "Barcode $code gehört zu einer Flasche im Bestand."
            return
        }
        busy = "Barcode wird nachgeschlagen …"
        scope.launch {
            lookupMessage = when (val r = ProductLookup.lookup(code)) {
                is ProductLookup.Result.Found -> {
                    applyProduct(r.info)
                    "Gefunden in Open Food Facts: ${listOfNotNull(r.info.brand, r.info.name).joinToString(" ")}"
                }
                ProductLookup.Result.NotFound -> "Barcode $code ist in Open Food Facts nicht bekannt – bitte Name eintippen. Er wird mit der Flasche gespeichert, der nächste Scan erkennt sie dann."
                is ProductLookup.Result.Failed -> "Nachschlagen nicht möglich: ${r.reason}. Der Barcode wird trotzdem gespeichert."
            }
            busy = null
        }
    }

    fun readLabel(path: String) {
        busy = "Etikett wird gelesen …"
        scanMessage = null
        scope.launch {
            try {
                val ocr = LabelScanner.recognize(context, File(path))
                val info = LabelParser.parse(ocr.lines, ocr.uncertain)
                detected = info.lines
                if (nameIsAuto || name.isBlank()) {
                    name = info.suggestedName ?: ""
                    nameIsAuto = true
                }
                info.sizeMl?.let { sizeText = it.toString() }
                info.abv?.let { abvText = formatAmount(it) }
                if (category.isBlank()) info.category?.let { category = it }
                if (ocr.lines.isNotEmpty()) labelText = ocr.lines.joinToString("\n")
                suggestMatch(BottleMatcher.byLabel(ocr.lines, others))
                scanMessage = when {
                    ocr.lines.isEmpty() -> "Kein Text erkannt – näher ran und auf gutes Licht achten."
                    info.suggestedName == null -> "Der Name ist schwer lesbar (z. B. Schreibschrift). Barcode scannen oder Textzeilen antippen."
                    else -> "Etikett gelesen. Textzeilen antippen, um Name oder Marke zu ergänzen."
                }
                // Ist der Barcode zufällig mit im Bild, gleich nutzen
                if (barcode.isBlank()) {
                    runCatching { BarcodeReader.read(context, File(path)) }.getOrNull()?.let {
                        barcode = it
                        lookupBarcode(it)
                    }
                }
            } catch (e: Exception) {
                scanMessage = "Texterkennung fehlgeschlagen: ${e.message}"
            } finally {
                if (busy == "Etikett wird gelesen …") busy = null
            }
        }
    }

    val photoActions = rememberPhotoActions { path ->
        photoPath = path
        readLabel(path)
    }

    // Barcode: eigenes Foto, das nach dem Lesen gleich wieder gelöscht wird
    var pendingBarcodePhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val barcodeCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingBarcodePhoto?.let(::File)
        pendingBarcodePhoto = null
        if (!ok || file == null) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }
        busy = "Barcode wird gelesen …"
        scope.launch {
            val code = runCatching { BarcodeReader.read(context, file) }.getOrNull()
            file.delete()
            busy = null
            if (code == null) {
                lookupMessage = "Kein Barcode erkannt. Nah ran, gerade halten, ohne Spiegelung – oder die Nummer unten eintippen."
            } else {
                barcode = code
                lookupBarcode(code)
            }
        }
    }
    fun scanBarcode() {
        val (file, uri) = Photos.newCaptureTarget(context)
        pendingBarcodePhoto = file.absolutePath
        try {
            barcodeCamera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Keine Kamera-App gefunden", Toast.LENGTH_SHORT).show()
        }
    }

    // "Etikett scannen" öffnet sofort die Kamera (nur einmal, auch nach dem Zurückkommen)
    var cameraStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startWithCamera && !cameraStarted) {
            cameraStarted = true
            photoActions.takePhoto()
        }
    }

    fun useExisting(b: Bottle) {
        base = b
        name = b.name
        nameIsAuto = false
        brand = b.brand
        category = b.category
        sizeText = b.sizeMl.toString()
        abvText = b.abv?.let(::formatAmount) ?: abvText
        fill = b.fillPercent.toFloat()
        count = b.bottleCount
        notes = b.notes
        if (b.labelText.isNotBlank()) labelText = b.labelText
        barcode = b.barcode ?: barcode
        b.price?.let { priceText = formatAmount(it) }
        if (photoPath == null) photoPath = b.photoPath
        match = null
        scanMessage = "Vorhandene Flasche übernommen. Jetzt den Füllstand messen und speichern."
    }

    /** Noch eine Flasche desselben Produkts: vorhandenen Eintrag übernehmen und die Anzahl um 1 erhöhen. */
    fun addOneMore(b: Bottle) {
        useExisting(b)
        count = b.bottleCount + 1
        scanMessage = "Eine Flasche mehr: jetzt $count Flaschen ${b.displayName()}. Zum Übernehmen speichern."
    }

    /** Falsch erkannt: Vorschlag verwerfen, gescannte Werte bleiben. */
    fun otherProduct() {
        match = null
        matchDismissed = true
    }

    var askMatchOnSave by remember { mutableStateOf(false) }

    fun save(ignoreMatch: Boolean = false) {
        if (match != null && !ignoreMatch) {
            askMatchOnSave = true
            return
        }
        if (name.isBlank() && brand.isBlank()) {
            nameError = true
            return
        }
        onSave(
            (base ?: Bottle(name = "")).copy(
                name = name.trim(),
                brand = brand.trim(),
                category = category.trim(),
                sizeMl = sizeText.trim().toIntOrNull()?.coerceIn(1, 20000) ?: 700,
                fillPercent = fill.roundToInt(),
                bottleCount = count,
                abv = abvText.trim().replace(',', '.').toDoubleOrNull(),
                notes = notes.trim(),
                labelText = labelText,
                photoPath = photoPath,
                barcode = barcode.trim().ifEmpty { null },
                price = priceText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 },
            ),
        )
    }

    val pendingMatch = match
    if (askMatchOnSave && pendingMatch != null) {
        AlertDialog(
            onDismissRequest = { askMatchOnSave = false },
            title = { Text("Diese Flasche gibt es schon") },
            text = { Text("„${pendingMatch.displayName()}“ ist schon im Bestand (${pendingMatch.stockLabel()}). Was möchtest du tun?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { askMatchOnSave = false; useExisting(pendingMatch) }) { Text("Vorhandene aktualisieren") }
                    TextButton(onClick = { askMatchOnSave = false; addOneMore(pendingMatch) }) { Text("Weitere Flasche (Anzahl +1)") }
                    TextButton(onClick = { askMatchOnSave = false; otherProduct(); save(ignoreMatch = true) }) { Text("Anderes Produkt speichern") }
                    TextButton(onClick = { askMatchOnSave = false }) { Text("Abbrechen") }
                }
            },
        )
    }

    if (confirmDelete && base != null) {
        val b = base!!
        ConfirmDeleteDialog(b.displayName(), onConfirm = { onDelete(b) }, onDismiss = { confirmDelete = false })
    }
    val currentPhoto = photoPath
    if (showFillDialog && currentPhoto != null) {
        FillLevelDialog(
            photoPath = currentPhoto,
            sizeMl = sizeText.toIntOrNull() ?: 700,
            onDismiss = { showFillDialog = false },
            onResult = { fill = it.toFloat(); showFillDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (base == null) "Neue Flasche" else "Flasche bearbeiten") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Abbrechen") } },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Löschen") }
                    }
                    TextButton(onClick = { save() }) { Text("Speichern") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoEditor(photoPath, photoActions, onRemove = { photoPath = null; detected = emptyList() })
            OutlinedButton(onClick = ::scanBarcode) { Text("▥  Barcode scannen") }

            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(it)
                }
            }

            match?.let { m ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Schon im Bestand?", style = MaterialTheme.typography.titleSmall)
                        Text("${m.displayName()} · ${m.sizeMl} ml · ${m.stockLabel()}")
                        Button(onClick = { useExisting(m) }, Modifier.fillMaxWidth()) { Text("Vorhandene aktualisieren") }
                        FilledTonalButton(onClick = { addOneMore(m) }, Modifier.fillMaxWidth()) { Text("Weitere Flasche (Anzahl +1)") }
                        OutlinedButton(onClick = ::otherProduct, Modifier.fillMaxWidth()) { Text("Anderes Produkt") }
                    }
                }
            }

            scanMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            lookupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }

            if (detected.isNotEmpty()) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Erkannter Text – antippen fügt hinzu zu:", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(targetIsName, { targetIsName = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Name") }
                            SegmentedButton(!targetIsName, { targetIsName = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Marke") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            detected.take(24).forEach { line ->
                                AssistChip(
                                    onClick = {
                                        if (targetIsName) {
                                            name = if (name.isBlank() || nameIsAuto) line else "$name $line"
                                            nameIsAuto = false
                                        } else {
                                            brand = if (brand.isBlank()) line else "$brand $line"
                                        }
                                    },
                                    label = { Text(line) },
                                )
                            }
                        }
                    }
                }
            } else if (photoPath != null && busy == null) {
                TextButton(onClick = { photoPath?.let(::readLabel) }) { Text("Etikett vom Foto lesen") }
            }

            OutlinedTextField(
                value = brand, onValueChange = { brand = it; nameError = false },
                modifier = Modifier.fillMaxWidth(), label = { Text("Marke / Hersteller") }, singleLine = true,
                trailingIcon = clearIcon(brand) { brand = "" },
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it; nameIsAuto = false; nameError = false },
                modifier = Modifier.fillMaxWidth(), label = { Text("Produkt / Name") }, singleLine = true,
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Name oder Marke eingeben") }
                } else null,
                trailingIcon = clearIcon(name) { name = ""; nameIsAuto = false },
            )
            FieldWithSuggestions("Kategorie", category, { category = it }, BOTTLE_CATEGORIES)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    sizeText, { sizeText = it.filter(Char::isDigit) }, Modifier.weight(1f),
                    label = { Text("Größe (ml)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    abvText, { abvText = it }, Modifier.weight(1f),
                    label = { Text("Alkohol (% vol)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            OutlinedTextField(
                priceText, { priceText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.fillMaxWidth(),
                label = { Text("Einkaufspreis pro Flasche (€)") }, singleLine = true,
                supportingText = { Text("Für die Kosten pro Drink in den Rezepten") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    barcode, { barcode = it.filter(Char::isDigit) }, Modifier.weight(1f),
                    label = { Text("Barcode (EAN)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FilledTonalButton(onClick = { lookupBarcode(barcode) }, enabled = barcode.length >= 8 && busy == null) {
                    Text("Nachschlagen")
                }
            }

            SectionTitle("Bestand")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Anzahl Flaschen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "inkl. der angebrochenen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { count = (count - 1).coerceAtLeast(0) }) { Text("−") }
                Text("$count", Modifier.width(40.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                FilledTonalIconButton(onClick = { count = (count + 1).coerceAtMost(999) }) { Text("+") }
            }

            val size = sizeText.toIntOrNull() ?: 700
            val totalMl = if (count <= 0) 0 else (count - 1) * size + size * fill.roundToInt() / 100
            Text(
                (if (count > 1) "Angebrochene Flasche: " else "Füllstand: ") + "${fill.roundToInt()} %   ·   gesamt ~$totalMl ml",
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(value = fill, onValueChange = { fill = it }, valueRange = 0f..100f, steps = 19, enabled = count > 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { showFillDialog = true }, enabled = photoPath != null && count > 0) {
                    Text(if (photoPath != null) "Am Foto messen" else "Messen: erst Foto machen")
                }
                if (count > 1) {
                    OutlinedButton(onClick = { count -= 1; fill = 100f }) { Text("Leer – nächste öffnen") }
                }
            }

            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notizen (Lieferant, Preis, Lagerort …)") }, minLines = 2)
        }
    }
}

private fun clearIcon(value: String, onClear: () -> Unit): (@Composable () -> Unit)? =
    if (value.isEmpty()) null else {
        { IconButton(onClick = onClear) { Icon(Icons.Default.Clear, contentDescription = "Leeren") } }
    }
