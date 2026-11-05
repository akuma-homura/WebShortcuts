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

/** Temporary, private normalized images; never copies a photo into saved bookmarks before Save. */
internal class EditorDraftStore(context: Context) {
    private val directory = File(context.cacheDir, "editor-drafts")

    suspend fun write(id: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        UUID.fromString(id)
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
        if (!file.isFile || file.length() > 5L * 1024 * 1024) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) return@withContext null
        BitmapFactory.decodeFile(file.path)
    }

    suspend fun delete(name: String?) = withContext(Dispatchers.IO) {
        fileFor(name)?.let { AtomicFile(it).delete() }
    }

    private fun fileFor(name: String?): File? {
        if (name == null || !name.matches(Regex("[0-9a-fA-F-]{36}(?:_[0-9a-fA-F-]{36})?\\.png"))) return null
        return File(directory, name)
    }

    suspend fun prune(keep: String?) = withContext(Dispatchers.IO) {
        val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.name != keep && it.lastModified() < expiry }?.forEach { it.delete() }
    }
}
