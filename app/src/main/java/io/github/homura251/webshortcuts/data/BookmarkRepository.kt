package io.github.homura251.webshortcuts.data

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local metadata storage. A completed disk transaction precedes every observable state update.
 * Corrupt or unsupported data remains untouched; subsequent writes are blocked until a successful
 * load, so an empty screen can never accidentally overwrite the user's collection.
 */
class BookmarkRepository(context: Context) {
    private val metadata = AtomicFile(File(context.filesDir, "bookmarks.json"))
    private val mutex = Mutex()
    private var attemptedLoad = false
    private val mutableBookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    private val mutableLoadError = MutableStateFlow<String?>(null)
    val bookmarks: StateFlow<List<BookmarkItem>> = mutableBookmarks.asStateFlow()
    val loadError: StateFlow<String?> = mutableLoadError.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { readFromDisk() }
    }

    suspend fun save(item: BookmarkItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureAvailable()
            val normalized = item.copy(title = item.title.trim(), url = UrlNormalizer.normalize(item.url))
            BookmarkCodec.validate(normalized)
            val old = mutableBookmarks.value
            val index = old.indexOfFirst { it.id == normalized.id }
            val updated = old.toMutableList().apply {
                if (index < 0) add(0, normalized) else set(index, normalized)
            }
            writeToDisk(updated)
        }
    }

    suspend fun delete(id: String): BookmarkItem? = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureAvailable()
            val deleted = mutableBookmarks.value.firstOrNull { it.id == id } ?: return@withLock null
            writeToDisk(mutableBookmarks.value.filterNot { it.id == id })
            deleted
        }
    }

    suspend fun find(id: String): BookmarkItem? = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureAvailable()
            mutableBookmarks.value.firstOrNull { it.id == id }
        }
    }

    suspend fun snapshot(): List<BookmarkItem> = withContext(Dispatchers.IO) {
        mutex.withLock { ensureAvailable(); mutableBookmarks.value.toList() }
    }

    /** Caller holds the app mutation mutex while staging images and committing the collection. */
    suspend fun replaceAll(items: List<BookmarkItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureAvailable()
            val normalized = items.map { it.copy(title = it.title.trim(), url = UrlNormalizer.normalize(it.url)) }
            writeToDisk(normalized)
        }
    }

    private fun ensureAvailable() {
        if (!attemptedLoad) readFromDisk()
        mutableLoadError.value?.let { throw IOException(it) }
    }

    private fun readFromDisk() {
        attemptedLoad = true
        try {
            // AtomicFile.openRead also restores an interrupted write's backup when applicable.
            val input = try { metadata.openRead() } catch (missing: java.io.FileNotFoundException) {
                if (metadata.baseFile.exists() || File(metadata.baseFile.path + ".bak").exists()) throw missing
                mutableBookmarks.value = emptyList()
                mutableLoadError.value = null
                return
            }
            val json = input.use { stream ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > BookmarkCodec.MAX_JSON_BYTES) throw IOException("收藏文件超过安全读取上限")
                    output.write(chunk, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
            mutableBookmarks.value = BookmarkCodec.decode(json)
            mutableLoadError.value = null
        } catch (failure: Exception) {
            mutableLoadError.value = "无法读取本地收藏，原文件已保留。${failure.message.orEmpty().take(160)}"
        }
    }

    private fun writeToDisk(items: List<BookmarkItem>) {
        val bytes = BookmarkCodec.encode(items).toByteArray(Charsets.UTF_8)
        require(bytes.size <= BookmarkCodec.MAX_JSON_BYTES) { "收藏文件超过安全存储上限" }
        val stream = metadata.startWrite()
        try {
            stream.write(bytes)
            metadata.finishWrite(stream)
        } catch (failure: Exception) {
            metadata.failWrite(stream)
            throw failure
        }
        mutableBookmarks.value = items.toList()
        mutableLoadError.value = null
    }
}
