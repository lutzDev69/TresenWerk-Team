package de.tresenwerk.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    recipeCount: Int,
    bottleCount: Int,
    message: String?,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onAddSamples: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Daten & Sicherung") }) }, bottomBar = bottomBar) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$recipeCount Rezepte", style = MaterialTheme.typography.titleMedium)
                    Text("$bottleCount Flaschen im Bestand", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Alles liegt nur auf diesem Gerät. Regelmäßig sichern!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Sicherung")
            Text(
                "Speichert alle Rezepte, Flaschen und Fotos in einer ZIP-Datei, z. B. in Google Drive, Nextcloud oder auf einem USB-Stick.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { exportLauncher.launch("tresenwerk-${LocalDate.now()}.zip") }) { Text("Sicherung erstellen") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream", "text/plain")) }) {
                Text("Sicherung einlesen")
            }
            Text(
                "Beim Einlesen werden Einträge samt Fotos hinzugefügt; Rezepte/Flaschen mit gleichem Namen werden übersprungen. Ältere JSON-Sicherungen (ohne Fotos) lassen sich weiterhin einlesen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Beispiele")
            OutlinedButton(onClick = onAddSamples) { Text("Klassiker hinzufügen (Daiquiri, Negroni, Old Fashioned)") }

            message?.let {
                Card { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.secondary) }
            }
        }
    }
}
