package de.tresenwerk.app.scan

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Ergebnis der Texterkennung. [uncertain] enthält Zeilen, bei denen sich die Erkennung unsicher war
 * (typisch bei Schreibschrift-Logos) – die werden nicht automatisch als Name vorgeschlagen.
 */
data class OcrResult(val lines: List<String>, val uncertain: Set<String>)

/** Liest Text von einem Flaschenfoto – komplett auf dem Gerät, ohne Internet (Google ML Kit). */
object LabelScanner {
    /** Unter dieser Sicherheit (0–1) gilt eine Zeile als unsicher. */
    const val MIN_CONFIDENCE = 0.6f

    /** Liefert die erkannten Zeilen, größte Schrift zuerst. */
    suspend fun recognize(context: Context, photo: File): OcrResult {
        val image = InputImage.fromFilePath(context, Uri.fromFile(photo))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val lines = text.textBlocks
                            .flatMap { it.lines }
                            .sortedByDescending { it.boundingBox?.height() ?: 0 }
                            .filter { it.text.isNotBlank() }
                        lines.forEach { Log.d("TresenWerk", "OCR %.2f  %s".format(it.confidence, it.text)) }
                        cont.resume(
                            OcrResult(
                                lines = lines.map { it.text.trim() }.distinct(),
                                uncertain = lines.filter { it.confidence < MIN_CONFIDENCE }.map { it.text.trim() }.toSet(),
                            ),
                        )
                    }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            recognizer.close()
        }
    }
}

/** Liest EAN/UPC-Barcodes von einem Foto – offline (Google ML Kit). */
object BarcodeReader {
    suspend fun read(context: Context, photo: File): String? {
        val image = InputImage.fromFilePath(context, Uri.fromFile(photo))
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return try {
            suspendCancellableCoroutine { cont ->
                scanner.process(image)
                    .addOnSuccessListener { codes -> cont.resume(codes.firstNotNullOfOrNull { it.rawValue?.trim()?.ifEmpty { null } }) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            scanner.close()
        }
    }
}
