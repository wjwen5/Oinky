package com.oinky.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.oinky.core.CategoryClassifier
import com.oinky.core.ReceiptData
import com.oinky.core.ReceiptParser
import kotlinx.coroutines.tasks.await

/**
 * On-device OCR (ML Kit, works offline) followed by [ReceiptParser].
 *
 * ML Kit returns text grouped in blocks, which on receipts often splits the item column from the
 * price column. We rebuild visual rows by vertical position first so "TOTAL ..... 21.05" ends up
 * on one line again.
 */
class ReceiptScanner(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(uri: Uri, classifier: CategoryClassifier, fallbackCurrency: String): Pair<ReceiptData, String> {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        val text = rowsOf(result).ifBlank { result.text }
        return ReceiptParser(classifier, fallbackCurrency).parse(text) to text
    }

    private fun rowsOf(result: Text): String {
        val lines = result.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        if (lines.isEmpty()) return ""
        val sorted = lines.sortedBy { it.boundingBox!!.centerY() }
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (line in sorted) {
            val box = line.boundingBox!!
            val row = rows.lastOrNull()
            val rowBox = row?.first()?.boundingBox
            if (row != null && rowBox != null && kotlin.math.abs(rowBox.centerY() - box.centerY()) < rowBox.height() / 2) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.boundingBox!!.left }.joinToString("  ") { it.text } }
    }
}
