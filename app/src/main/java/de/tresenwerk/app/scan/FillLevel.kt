package de.tresenwerk.app.scan

import kotlin.math.roundToInt

/**
 * Füllstand aus drei Linien auf dem Flaschenfoto (Werte 0 = oben, 1 = unten im Bild):
 * [bottom] = innerer Flaschenboden, [full] = Höhe bei voller Flasche (meist die Schulter),
 * [level] = aktuelle Flüssigkeitsoberfläche.
 * Rechnet linear – passt gut für den geraden Flaschenkörper, im Hals wird auf 100 % gekappt.
 */
object FillLevel {
    fun percent(bottom: Float, full: Float, level: Float): Int {
        val span = bottom - full
        if (span <= 0.01f) return 0
        return ((bottom - level) / span * 100f).roundToInt().coerceIn(0, 100)
    }

    /** Restmenge in ml. */
    fun remainingMl(sizeMl: Int, percent: Int): Int = (sizeMl * percent / 100f).roundToInt()
}
