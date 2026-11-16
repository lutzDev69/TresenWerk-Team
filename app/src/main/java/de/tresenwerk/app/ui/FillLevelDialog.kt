package de.tresenwerk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import de.tresenwerk.app.scan.FillLevel
import de.tresenwerk.app.util.Photos
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Füllstand am Foto messen: drei Linien werden auf Boden, Voll-Höhe und Flüssigkeit geschoben.
 * Die nächstgelegene Linie folgt dem Finger (Ziehen oder Antippen).
 */
@Composable
fun FillLevelDialog(photoPath: String, sizeMl: Int, onDismiss: () -> Unit, onResult: (Int) -> Unit) {
    val ratio = remember(photoPath) { Photos.aspectRatio(photoPath) }
    var bottom by remember { mutableFloatStateOf(0.88f) }
    var full by remember { mutableFloatStateOf(0.35f) }
    var level by remember { mutableFloatStateOf(0.6f) }
    var active by remember { mutableIntStateOf(-1) }
    val percent = FillLevel.percent(bottom, full, level)

    fun nearest(y: Float): Int = listOf(bottom, full, level).withIndex().minBy { abs(it.value - y) }.index
    fun move(index: Int, y: Float) {
        val v = y.coerceIn(0f, 1f)
        when (index) {
            0 -> bottom = v
            1 -> full = v
            2 -> level = v
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Füllstand messen", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Linien auf dem Foto verschieben: grün auf den Flaschenboden (innen), blau auf die Höhe einer vollen Flasche (meist die Schulter), gelb auf die Flüssigkeit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)) {
                        AsyncImage(
                            model = File(photoPath),
                            contentDescription = "Flaschenfoto",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { pos ->
                                        val y = pos.y / size.height
                                        move(nearest(y), y)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { pos -> active = nearest(pos.y / size.height) },
                                        onDragEnd = { active = -1 },
                                        onDragCancel = { active = -1 },
                                    ) { change, _ ->
                                        change.consume()
                                        if (active >= 0) move(active, change.position.y / size.height)
                                    }
                                },
                        ) {
                            val w = size.width
                            val h = size.height
                            drawRect(
                                Amber.copy(alpha = 0.22f),
                                topLeft = Offset(0f, level * h),
                                size = Size(w, max(0f, (bottom - level) * h)),
                            )
                            listOf(bottom to Mint, full to Sky, level to Amber).forEachIndexed { i, (f, color) ->
                                val y = f * h
                                val stroke = if (i == active) 5.dp.toPx() else 3.dp.toPx()
                                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), stroke + 2.dp.toPx())
                                drawLine(color, Offset(0f, y), Offset(w, y), stroke)
                                drawCircle(color, 11.dp.toPx(), Offset(w - 18.dp.toPx(), y))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(Mint, "Boden")
                    Legend(Sky, "Voll")
                    Legend(Amber, "Flüssigkeit")
                }
                Text(
                    "Geschätzt: $percent %  (~${FillLevel.remainingMl(sizeMl, percent)} ml von $sizeMl ml)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(onClick = { onResult(percent) }) { Text("Übernehmen") }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
