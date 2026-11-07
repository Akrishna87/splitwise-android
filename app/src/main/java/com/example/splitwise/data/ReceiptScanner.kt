package com.example.splitwise.data

import android.content.Context
import android.net.Uri
import com.example.splitwise.domain.ParsedReceipt
import com.example.splitwise.domain.ReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR (ML Kit, bundled model: works offline, image never leaves the phone). */
object ReceiptScanner {
    private data class Line(val text: String, val cy: Int, val h: Int, val left: Int)

    suspend fun scan(ctx: Context, file: File): ParsedReceipt = withContext(Dispatchers.Default) {
        val image = InputImage.fromFilePath(ctx, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                l.boundingBox?.let { Line(l.text, it.centerY(), maxOf(it.height(), 1), it.left) }
            }.sortedBy { it.cy }
            // Join text that sits on the same visual row (item name on the left, price on the right).
            val rows = mutableListOf<String>()
            var row = mutableListOf<Line>()
            fun flush() { if (row.isNotEmpty()) rows += row.sortedBy { it.left }.joinToString(" ") { it.text }; row = mutableListOf() }
            for (l in lines) {
                if (row.isNotEmpty() && Math.abs(l.cy - row.first().cy) > row.first().h * 0.6) flush()
                row += l
            }
            flush()
            ReceiptParser.parse(rows)
        } finally {
            recognizer.close()
        }
    }
}
