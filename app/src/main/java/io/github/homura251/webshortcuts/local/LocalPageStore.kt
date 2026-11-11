package io.github.homura251.webshortcuts.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ImportedLocalPage(
    val fileName: String,
    val displayName: String,
    val sourceType: BookmarkSourceType,
    val sizeBytes: Long,
)

/**
 * A selected document is copied into private storage before the editor sees it. No persisted URI
 * permission is necessary, so the saved shortcut keeps working after the source moves or vanishes.
 * Writers use fresh names and rename a complete, validated temporary file in the same directory.
 */
class LocalPageStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.filesDir, DIRECTORY)

    suspend fun importDocument(uri: Uri): ImportedLocalPage = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "请通过系统文件选择器选择 HTML 或 MHTML 文件" }
        val metadata = metadata(uri)
        require(metadata.second == null || metadata.second!! <= MAX_FILE_BYTES) { "本地网页不能超过 32 MB" }
        val mime = resolver.getType(uri)
        val type = LocalPagePolicy.detectType(metadata.first, mime)
        val name = newName(type)
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取所选文件，请重新选择")
        val file = input.use { stream ->
            write(name, stream) { coroutineContext.ensureActive() }
        }
        ImportedLocalPage(name, metadata.first, type, file.length())
    }

    /** Resolves only a single generated basename, never an arbitrary relative or absolute path. */
    fun resolve(fileName: String): File? {
        if (!isSafeFileName(fileName)) return null
        val file = File(directory, fileName)
        return try {
            file.takeIf { it.isFile && it.canonicalFile.parentFile == directory.canonicalFile }
        } catch (_: IOException) { null }
    }

    fun exportFile(fileName: String): File? = resolve(fileName)

    /** Backup preview can reject malformed content without mutating private storage. */
    fun validateBytes(fileName: String, bytes: ByteArray) {
        require(isSafeFileName(fileName)) { "本地网页文件名无效" }
        LocalPagePolicy.validateBytes(typeForFileName(fileName), bytes)
    }

    /** Must run on an I/O dispatcher; a restored backup never overwrites an existing file. */
    fun restoreBytes(fileName: String, bytes: ByteArray): String {
        validateBytes(fileName, bytes)
        val name = newName(typeForFileName(fileName))
        bytes.inputStream().use { write(name, it) }
        return name
    }

    /**
     * Caller holds the app mutation mutex. The grace period also protects a just-imported editor
     * draft from unrelated cleanup before its metadata transaction is committed.
     */
    fun cleanup(items: List<BookmarkItem>, protectedFileNames: Set<String> = emptySet()) {
        val referenced = items.mapNotNull { it.localFileName }.toSet() + protectedFileNames
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MILLIS
        directory.listFiles()?.forEach { file ->
            val basename = if (file.name.endsWith(".part")) file.name.removeSuffix(".part") else file.name
            if (isSafeFileName(basename) && basename !in referenced && file.lastModified() < cutoff) {
                // A process can die before write() reaches finally. Expired staging files have
                // the same strict basename and containment checks as completed orphan files.
                val contained = runCatching { file.isFile && file.canonicalFile.parentFile == directory.canonicalFile }.getOrDefault(false)
                if (contained) file.delete()
            }
        }
    }

    /** For a specifically abandoned staged file; caller first verifies it has no committed owner. */
    fun delete(fileName: String): Boolean = resolve(fileName)?.delete() ?: false

    private fun write(name: String, input: InputStream, checkActive: () -> Unit = {}): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建本地网页目录")
        val temporary = File(directory, "$name.part")
        val destination = File(directory, name)
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    require(total <= MAX_FILE_BYTES) { "本地网页不能超过 32 MB" }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            checkActive()
            LocalPagePolicy.validateFile(typeForFileName(name), temporary)
            checkActive()
            if (!temporary.renameTo(destination)) throw IOException("无法保存本地网页，请重试")
            return destination
        } finally {
            temporary.delete()
        }
    }

    private fun metadata(uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn).takeIf { it >= 0 }
            }
        }
        val displayName = (name ?: uri.lastPathSegment ?: "本地网页")
            .substringAfterLast('/').substringAfterLast('\\')
            .filterNot { it.isISOControl() }.trim().take(200).ifBlank { "本地网页" }
        return displayName to size
    }

    private fun newName(type: BookmarkSourceType) = UUID.randomUUID().toString() +
        if (type == BookmarkSourceType.MHTML) ".mhtml" else ".html"

    companion object {
        const val DIRECTORY = "local_pages"
        const val MAX_FILE_BYTES = 32L * 1024L * 1024L
        private const val ORPHAN_GRACE_MILLIS = 24L * 60L * 60L * 1000L
        private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,100}\\.(html|mhtml)")
        fun isSafeFileName(name: String): Boolean = SAFE_NAME.matches(name)
        fun typeForFileName(name: String): BookmarkSourceType {
            require(isSafeFileName(name)) { "本地网页文件名无效" }
            return if (name.endsWith(".mhtml")) BookmarkSourceType.MHTML else BookmarkSourceType.HTML
        }
    }
}
