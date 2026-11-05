package io.github.homura251.webshortcuts.icons

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Two safe normalized previews; no original photo or long-lived URI permission is retained. */
data class ImportedIcon(val fitted: Bitmap, val cropped: Bitmap)

/** Accepts a scoped Photo Picker / OpenDocument URI; never asks for broad storage access. */
class IconImporter(context: Context, private val processor: IconProcessor) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun import(uri: Uri, centerCrop: Boolean = false): Bitmap = withContext(Dispatchers.IO) {
        val (bytes, mime) = read(uri)
        coroutineContext.ensureActive()
        val importContext = coroutineContext
        processor.decode(bytes, mime, centerCrop, checkpoint = { importContext.ensureActive() })
    }

    suspend fun importVariants(uri: Uri): ImportedIcon = withContext(Dispatchers.IO) {
        val (bytes, mime) = read(uri)
        coroutineContext.ensureActive()
        val importContext = coroutineContext
        val fitted = processor.decode(bytes, mime, centerCrop = false, checkpoint = { importContext.ensureActive() })
        try {
            coroutineContext.ensureActive()
            ImportedIcon(fitted, processor.decode(bytes, mime, centerCrop = true, checkpoint = { importContext.ensureActive() }))
        } catch (failure: Exception) {
            fitted.recycle()
            throw failure
        }
    }

    private fun read(uri: Uri): Pair<ByteArray, String?> {
        require(uri.scheme == "content") { "Choose an image with the system picker." }
        val bytes = resolver.openInputStream(uri)?.use { IconPolicy.readBounded(it, IconPolicy.MAX_IMAGE_BYTES) }
            ?: throw IOException("The selected image is no longer available.")
        // Providers occasionally expose application/octet-stream. Verify the actual signature instead.
        val mime = resolver.getType(uri)?.takeIf { it in IconPolicy.supportedMimeTypes }
        return bytes to mime
    }
}
