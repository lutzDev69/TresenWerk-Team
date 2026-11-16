package de.tresenwerk.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Fotos werden verkleinert (max. 1600 px), richtig gedreht und im privaten App-Ordner gespeichert.
 * So brauchen sie wenig Platz und Galerie-Bilder dürfen später gelöscht werden.
 */
object Photos {
    private const val MAX_SIZE = 1600

    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    /** Leere Datei + teilbare Adresse für die Kamera-App. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val folder = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(folder, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Übernimmt ein Bild (Kamera oder Galerie) und liefert den Pfad der gespeicherten Kopie. */
    suspend fun import(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val rotation = resolver.openInputStream(source)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val scale = minOf(1f, MAX_SIZE.toFloat() / max(decoded.width, decoded.height))
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation)
            }
            val result = if (scale < 1f || rotation != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else decoded

            val target = File(dir(context), "${UUID.randomUUID()}.jpg")
            target.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            target.absolutePath
        }.getOrNull()
    }

    /** Seitenverhältnis Breite/Höhe eines gespeicherten Fotos. */
    fun aspectRatio(path: String): Float {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 0.75f
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    /** Löscht Fotos, die zu keinem Rezept / keiner Flasche mehr gehören (z. B. nach "Abbrechen"). */
    fun cleanup(context: Context, used: Set<String>) {
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir(context).listFiles()?.forEach { f ->
            if (f.absolutePath !in used && f.lastModified() < cutoff) f.delete()
        }
        File(context.cacheDir, "captures").listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) f.delete()
        }
    }
}
