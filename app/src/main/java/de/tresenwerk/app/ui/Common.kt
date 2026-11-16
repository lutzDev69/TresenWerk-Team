package de.tresenwerk.app.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.tresenwerk.app.util.Photos
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun Thumb(path: String?, fallback: String, size: Dp = 56.dp) {
    val shape = RoundedCornerShape(10.dp)
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fallback.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** Textfeld mit antippbaren Vorschlägen darunter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldWithSuggestions(label: String, value: String, onChange: (String) -> Unit, suggestions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { s -> SuggestionChip(onClick = { onChange(s) }, label = { Text(s) }) }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Löschen?") },
        text = { Text("„$what“ wird endgültig gelöscht.") },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

class PhotoActions(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

/** Kamera und Galerie. [onPhoto] bekommt den Pfad der gespeicherten, verkleinerten Kopie. */
@Composable
fun rememberPhotoActions(onPhoto: (String) -> Unit): PhotoActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPhoto)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun take(uri: Uri, cleanup: File? = null) {
        scope.launch {
            val path = Photos.import(context, uri)
            cleanup?.delete()
            if (path != null) callback(path)
            else Toast.makeText(context, "Foto konnte nicht gelesen werden", Toast.LENGTH_SHORT).show()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pending?.let(::File)
        pending = null
        if (ok && file != null) take(Uri.fromFile(file), cleanup = file) else file?.delete()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) take(uri)
    }
    return remember {
        PhotoActions(
            takePhoto = {
                val (file, uri) = Photos.newCaptureTarget(context)
                pending = file.absolutePath
                try {
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "Keine Kamera-App gefunden", Toast.LENGTH_SHORT).show()
                }
            },
            pickPhoto = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

/** Foto-Vorschau mit Knöpfen Kamera / Galerie / Entfernen. */
@Composable
fun PhotoEditor(path: String?, actions: PhotoActions, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = "Foto",
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.takePhoto) { Text("Kamera") }
            OutlinedButton(onClick = actions.pickPhoto) { Text("Galerie") }
            if (path != null) {
                TextButton(onClick = onRemove) { Text("Foto entfernen") }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier.width(280.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

val METHODS = listOf("Shaken", "Stirred", "Built", "Throwing", "Blended", "Swizzle")
val GLASSES = listOf("Coupe", "Nick & Nora", "Martini", "Rocks", "Highball", "Collins", "Julep", "Tiki", "Weinglas")
val ICES = listOf("Würfel", "Großer Würfel", "Crushed", "Kugel", "Ohne")
val BOTTLE_CATEGORIES = listOf(
    "Gin", "Rum", "Whisk(e)y", "Vodka", "Tequila", "Mezcal", "Brandy",
    "Likör", "Wermut", "Bitter", "Amaro", "Sirup", "Sonstiges",
)
