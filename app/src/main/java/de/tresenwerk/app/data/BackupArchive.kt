package de.tresenwerk.app.data

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import de.tresenwerk.app.util.TooLargeException
import de.tresenwerk.app.util.copyAtMost
import de.tresenwerk.app.util.readAtMost

/**
 * ZIP-Sicherung: "backup.json" plus alle Fotos im Ordner "photos/".
 * Alte Sicherungen (reine JSON-Dateien aus Version 0.1/0.2) können weiterhin eingelesen werden.
 */
object BackupArchive {
    const val JSON_ENTRY = "backup.json"

    /** Obergrenzen beim Einlesen – schützen vor manipulierten Dateien, die den Speicher füllen würden. */
    data class Limits(
        val maxJsonBytes: Long = 20_000_000,
        val maxPhotoBytes: Long = 20_000_000,
        val maxTotalBytes: Long = 1_000_000_000,
        val maxEntries: Int = 10_000,
    )

    /** Schreibt die Sicherung. Fehlende Fotodateien werden übersprungen. Gibt die Zahl der Fotos zurück. */
    fun write(out: OutputStream, json: String, photos: List<File>): Int {
        var count = 0
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.toByteArray())
            zip.closeEntry()
            photos.filter { it.isFile }.distinctBy { it.name }.forEach { photo ->
                // Fotos sind schon JPEG-komprimiert – nochmal packen spart nichts, kostet nur Zeit
                zip.setLevel(0)
                zip.putNextEntry(ZipEntry(Backup.PHOTO_FOLDER + photo.name))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        return count
    }

    /** Inhalt einer eingelesenen Sicherung. [photos] ordnet Eintragsnamen ("photos/x.jpg") entpackten Dateien zu. */
    class Content(val json: String, val photos: Map<String, File>)

    /**
     * Liest eine ZIP- oder JSON-Sicherung. Fotos werden nach [tempDir] entpackt.
     * Nur einfache Dateinamen mit Bildendung werden angenommen (Schutz gegen manipulierte Archive).
     */
    fun read(input: InputStream, tempDir: File, limits: Limits = Limits()): Content {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val isZip = buffered.read() == 'P'.code && buffered.read() == 'K'.code
        buffered.reset()
        if (!isZip) return Content(buffered.readAtMost(limits.maxJsonBytes, "Die Sicherung").decodeToString(), emptyMap())

        tempDir.mkdirs()
        var json: String? = null
        val photos = mutableMapOf<String, File>()
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(buffered).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > limits.maxEntries) throw TooLargeException("Die Sicherung (zu viele Einträge)", limits.maxTotalBytes)
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == JSON_ENTRY -> {
                        val bytes = zip.readAtMost(limits.maxJsonBytes, "backup.json")
                        totalBytes += bytes.size
                        json = bytes.decodeToString()
                    }
                    name.startsWith(Backup.PHOTO_FOLDER) && isSafePhotoName(name.removePrefix(Backup.PHOTO_FOLDER)) -> {
                        val target = File(tempDir, name.removePrefix(Backup.PHOTO_FOLDER))
                        totalBytes += target.outputStream().use { zip.copyAtMost(it, limits.maxPhotoBytes, "Ein Foto in der Sicherung") }
                        photos[name] = target
                    }
                }
                if (totalBytes > limits.maxTotalBytes) throw TooLargeException("Die Sicherung", limits.maxTotalBytes)
                zip.closeEntry()
            }
        }
        return Content(json ?: error("Keine TresenWerk-Sicherung (backup.json fehlt)"), photos)
    }

    private fun isSafePhotoName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 100 &&
            name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } &&
            !name.startsWith(".") &&
            name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
}
