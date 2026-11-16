package de.tresenwerk.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFE0A84F)
val Mint = Color(0xFF8FBFA8)
val Sky = Color(0xFF6FA8DC)
val LowStock = Color(0xFFE57368)

/** Bewusst immer dunkel: an der Bar ist es meist gedimmt, ein helles Display blendet. */
private val colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241A0A),
    primaryContainer = Color(0xFF4A3618),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Mint,
    onSecondary = Color(0xFF0F2A20),
    secondaryContainer = Color(0xFF2A3D35),
    onSecondaryContainer = Color(0xFFCDEBDD),
    background = Color(0xFF14110F),
    onBackground = Color(0xFFEDE6DF),
    surface = Color(0xFF14110F),
    onSurface = Color(0xFFEDE6DF),
    surfaceVariant = Color(0xFF2A2420),
    onSurfaceVariant = Color(0xFFCFC4B9),
    surfaceContainer = Color(0xFF1E1A17),
    surfaceContainerHigh = Color(0xFF26211D),
    surfaceContainerHighest = Color(0xFF302A25),
    outline = Color(0xFF8A7F75),
    error = LowStock,
)

@Composable
fun TresenWerkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
