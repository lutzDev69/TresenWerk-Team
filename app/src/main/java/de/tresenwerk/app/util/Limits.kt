package de.tresenwerk.app.util

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Wird geworfen, wenn eine Datei oder Antwort größer ist als erlaubt (Schutz vor manipulierten Dateien). */
class TooLargeException(what: String, limit: Long) : IOException("$what ist zu groß (mehr als ${limit / 1_000_000} MB)")

/** Liest höchstens [max] Bytes; ist mehr da, gibt es eine [TooLargeException]. */
fun InputStream.readAtMost(max: Long, what: String): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    copyAtMost(out, max, what)
    return out.toByteArray()
}

/** Kopiert höchstens [max] Bytes; ist mehr da, gibt es eine [TooLargeException]. Gibt die kopierten Bytes zurück. */
fun InputStream.copyAtMost(out: OutputStream, max: Long, what: String): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) return total
        total += n
        if (total > max) throw TooLargeException(what, max)
        out.write(buffer, 0, n)
    }
}
