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
import java.io.OutputStream
import java.util.UUID

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
            write(relative, bitmap)
        }
    }

    /** Save an editable original separately from the already normalized launcher image. */
    suspend fun saveOriginal(id: String, version: Int, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid bookmark id." }
        require(version >= 1)
        require(bitmap.width in 1..IconProcessor.MAX_EDITABLE_SIZE && bitmap.height in 1..IconProcessor.MAX_EDITABLE_SIZE)
        mutex.withLock { write("icons/original/${id}_v$version.png", bitmap) }
    }

    suspend fun read(path: String?): Bitmap? = readImage(path, original = false)

    suspend fun readOriginal(path: String?): Bitmap? = readImage(path, original = true)

    /** AtomicFile recovery and bounded reads also apply when taking a backup snapshot. */
    suspend fun exportBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: return@withContext null
        mutex.withLock {
            if (!atomicFamilyIsLocal(file)) return@withLock null
            runCatching {
                val original = path.startsWith("icons/original/")
                val bytes = AtomicFile(file).openRead().use {
                    IconPolicy.readBounded(it, if (original) IconProcessor.MAX_EDITABLE_BYTES else IconPolicy.MAX_IMAGE_BYTES)
                }
                StoredIconBytes.validate(bytes, original, path.substringAfterLast('.'))
                bytes
            }.getOrNull()
        }
    }

    /** Fresh names ensure a failed import never overwrites a currently referenced image. */
    suspend fun restoreBytes(pathHint: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(resolve(pathHint) != null) { "备份图像路径无效" }
        val original = pathHint.startsWith("icons/original/")
        StoredIconBytes.validate(bytes, original, pathHint.substringAfterLast('.'))
        val relative = "${pathHint.substringBeforeLast('/')}/import_${UUID.randomUUID()}.${pathHint.substringAfterLast('.')}"
        mutex.withLock { writeAtomically(relative) { it.write(bytes) } }
    }

    private suspend fun readImage(path: String?, original: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        if (path == null || path.startsWith("icons/original/") != original) return@withContext null
        val file = resolve(path) ?: return@withContext null
        mutex.withLock {
            if (!atomicFamilyIsLocal(file)) return@withLock null
            runCatching {
                // openRead restores AtomicFile's legacy .bak after an interrupted write. Reading the
                // base file directly can lose a recoverable icon or decode a partially written base.
                val limit = if (original) IconProcessor.MAX_EDITABLE_BYTES else IconPolicy.MAX_IMAGE_BYTES
                val bytes = AtomicFile(file).openRead().use { IconPolicy.readBounded(it, limit) }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                IconPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
                if (original) require(bounds.outWidth <= IconProcessor.MAX_EDITABLE_SIZE && bounds.outHeight <= IconProcessor.MAX_EDITABLE_SIZE)
                var sample = 1
                if (!original) while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }.getOrNull()
        }
    }

    private fun write(relative: String, bitmap: Bitmap): String = writeAtomically(relative) {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not encode icon.")
    }

    private fun writeAtomically(relative: String, action: (OutputStream) -> Unit): String {
        val file = resolve(relative) ?: throw IOException("Invalid icon path.")
        if (!atomicFamilyIsLocal(file)) throw IOException("Invalid icon path.")
        if (!file.parentFile!!.isDirectory && !file.parentFile!!.mkdirs()) throw IOException("Could not create icon directory.")
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            action(stream)
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
        return relative
    }

    /** Run only after metadata commits, retaining both AUTO and CUSTOM histories still referenced. */
    suspend fun cleanup(bookmarks: List<BookmarkItem>) = withContext(Dispatchers.IO) {
        val retained = bookmarks.flatMap { listOfNotNull(it.autoImagePath, it.customImagePath, it.customOriginalPath) }
            .mapNotNull(::resolve).flatMap { file ->
                // Keep the whole AtomicFile family until openRead can perform recovery. In particular,
                // the base may be absent while its only committed copy is still named .bak.
                listOf(file.canonicalPath, file.canonicalPath + ".bak", file.canonicalPath + ".new")
            }.toSet()
        mutex.withLock {
            listOf("auto", "custom", "original").forEach { name ->
                val directory = File(root, name)
                // Do not follow a symlink or accept a path beyond the application's icon root.
                if (root.canonicalFile != File(filesDir.canonicalFile, "icons") || !directory.isDirectory ||
                    directory.canonicalFile != File(root.canonicalFile, name)) return@forEach
                directory.listFiles()?.forEach { file ->
                    if (file.isFile && file.canonicalFile == File(directory.canonicalFile, file.name) && file.canonicalPath !in retained) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun resolve(relative: String): File? {
        if (!relative.matches(Regex("icons/(?:(?:auto|custom)/[A-Za-z0-9_-]{1,100}\\.(?:png|webp)|original/[A-Za-z0-9_-]{1,100}\\.png)"))) return null
        val file = File(filesDir, relative)
        if (root.canonicalFile != File(filesDir.canonicalFile, "icons")) return null
        if (file.canonicalFile != File(filesDir.canonicalFile, relative)) return null
        return file
    }

    private fun atomicFamilyIsLocal(file: File): Boolean = listOf("", ".bak", ".new").all { suffix ->
        val sibling = File(file.path + suffix)
        sibling.canonicalFile == File(file.parentFile!!.canonicalFile, file.name + suffix)
    }
}
