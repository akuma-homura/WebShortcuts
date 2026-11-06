package io.github.homura251.webshortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import io.github.homura251.webshortcuts.icons.IconPolicy
import io.github.homura251.webshortcuts.icons.IconProcessor

/** Temporary, private originals/previews; no image becomes a saved bookmark before Save. */
internal class EditorDraftStore(context: Context) {
    private val directory = File(context.cacheDir, "editor-drafts")

    suspend fun write(id: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        UUID.fromString(id)
        require(bitmap.width in 1..IconProcessor.MAX_EDITABLE_SIZE && bitmap.height in 1..IconProcessor.MAX_EDITABLE_SIZE)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法保存图片草稿")
        val name = "${id}_${UUID.randomUUID()}.png"
        val target = AtomicFile(File(directory, name))
        val output = target.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
        name
    }

    suspend fun read(name: String?): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(name) ?: return@withContext null
        val bytes = runCatching {
            AtomicFile(file).openRead().use { IconPolicy.readBounded(it, IconProcessor.MAX_EDITABLE_BYTES) }
        }.getOrNull() ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..IconProcessor.MAX_EDITABLE_SIZE || bounds.outHeight !in 1..IconProcessor.MAX_EDITABLE_SIZE) return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    suspend fun delete(name: String?) = withContext(Dispatchers.IO) {
        fileFor(name)?.let { AtomicFile(it).delete() }
    }

    private fun fileFor(name: String?): File? {
        if (name == null || !name.matches(Regex("[0-9a-fA-F-]{36}(?:_[0-9a-fA-F-]{36})?\\.png"))) return null
        return File(directory, name)
    }

    suspend fun prune(keep: String?) = prune(setOfNotNull(keep))

    suspend fun prune(keep: Set<String>) = withContext(Dispatchers.IO) {
        val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val retained = keep.flatMap { listOf(it, "$it.bak", "$it.new") }.toSet()
        directory.listFiles()?.filter { it.name !in retained && it.lastModified() < expiry }?.forEach { it.delete() }
    }
}
