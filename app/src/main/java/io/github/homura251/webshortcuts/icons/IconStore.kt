package io.github.homura251.webshortcuts.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Private, versioned PNG files. Metadata stores relative paths only, never image bytes. */
class IconStore(context: Context) {
    private val filesDir = context.applicationContext.filesDir
    private val root = File(filesDir, "icons")
    private val mutex = Mutex()

    suspend fun save(id: String, version: Int, mode: IconSourceMode, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid bookmark id." }
        require(version >= 1)
        require(mode != IconSourceMode.GENERATED) { "Generated icons do not require files." }
        require(bitmap.width == IconPolicy.OUTPUT_SIZE && bitmap.height == IconPolicy.OUTPUT_SIZE)
        mutex.withLock {
            val kind = if (mode == IconSourceMode.CUSTOM) "custom" else "auto"
            val relative = "icons/$kind/${id}_v$version.png"
            val file = resolve(relative) ?: throw IOException("Invalid icon path.")
            if (!file.parentFile!!.isDirectory && !file.parentFile!!.mkdirs()) throw IOException("Could not create icon directory.")
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IOException("Could not encode icon.")
                atomic.finishWrite(stream)
            } catch (failure: Exception) {
                atomic.failWrite(stream)
                throw failure
            }
            relative
        }
    }

    suspend fun read(path: String?): Bitmap? = withContext(Dispatchers.IO) {
        val file = path?.let(::resolve) ?: return@withContext null
        mutex.withLock {
            if (!file.isFile || file.length() !in 1..IconPolicy.MAX_IMAGE_BYTES.toLong()) return@withLock null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                IconPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }.getOrNull()
        }
    }

    /** Run only after metadata commits, retaining both AUTO and CUSTOM histories still referenced. */
    suspend fun cleanup(bookmarks: List<BookmarkItem>) = withContext(Dispatchers.IO) {
        val retained = bookmarks.flatMap { listOfNotNull(it.autoImagePath, it.customImagePath) }
            .mapNotNull(::resolve).map { it.canonicalPath }.toSet()
        mutex.withLock {
            listOf("auto", "custom").forEach { name ->
                val directory = File(root, name)
                // Do not follow a symlink or accept a path beyond the application's icon root.
                if (!directory.isDirectory || directory.canonicalPath != directory.absolutePath) return@forEach
                directory.listFiles()?.forEach { file ->
                    if (file.isFile && file.canonicalPath == file.absolutePath && file.canonicalPath !in retained) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun resolve(relative: String): File? {
        if (!relative.matches(Regex("icons/(auto|custom)/[A-Za-z0-9_-]{1,100}\\.(png|webp)"))) return null
        val file = File(filesDir, relative)
        if (root.canonicalFile.parentFile != filesDir.canonicalFile) return null
        if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator)) return null
        return file
    }
}
