package io.github.homura251.webshortcuts.local

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPageStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LocalPageStore(context)
    private val provider = DocumentProvider()

    @Before fun prepare() {
        File(context.filesDir, LocalPageStore.DIRECTORY).deleteRecursively()
        provider.attachInfo(context, ProviderInfo().apply { authority = AUTHORITY })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
    }

    @Test fun importsUppercaseHtmlAsAnIndependentPrivateCopy() = runBlocking {
        val bytes = "<!doctype html><meta charset='utf-8'><h1>旅行手记 · 离线</h1>".toByteArray()
        val source = source("旅行手记.HTML", "application/octet-stream", bytes)
        val result = store.importDocument(uri)
        assertEquals("旅行手记.HTML", result.displayName)
        assertEquals(BookmarkSourceType.HTML, result.sourceType)
        assertEquals(bytes.size.toLong(), result.sizeBytes)
        assertTrue(result.fileName.endsWith(".html"))
        assertTrue(LocalPageStore.isSafeFileName(result.fileName))
        source.delete()
        assertArrayEquals(bytes, store.resolve(result.fileName)!!.readBytes())
    }

    @Test fun acceptsMimeTypeWhenAProviderOmitsTheExtension() = runBlocking {
        source("下载的网页", "text/html", HTML)
        assertEquals(BookmarkSourceType.HTML, store.importDocument(uri).sourceType)
        source("下载的归档", "application/mhtml", ARCHIVE)
        assertEquals(BookmarkSourceType.MHTML, store.importDocument(uri).sourceType)
    }

    @Test fun mhtIsStoredAsMhtmlAndCanBeRestoredWithoutOverwriting() = runBlocking {
        source("report.MHT", "application/octet-stream", ARCHIVE)
        val imported = store.importDocument(uri)
        assertEquals(BookmarkSourceType.MHTML, imported.sourceType)
        val first = store.restoreBytes(imported.fileName, ARCHIVE)
        val second = store.restoreBytes(imported.fileName, ARCHIVE)
        assertNotEquals(imported.fileName, first)
        assertNotEquals(first, second)
        assertArrayEquals(ARCHIVE, store.exportFile(second)!!.readBytes())
    }

    @Test fun malformedAndUnsupportedFilesNeverLeavePartialCopies() = runBlocking {
        for ((name, bytes) in listOf("empty.html" to ByteArray(0), "broken.html" to byteArrayOf(0, 1, 2),
            "broken.mht" to "<html>not an archive</html>".toByteArray(),
            "truncated.mhtml" to ARCHIVE.copyOf(ARCHIVE.size - 15), "document.pdf" to HTML)) {
            source(name, "application/octet-stream", bytes)
            assertThrows(Exception::class.java) { runBlocking { store.importDocument(uri) } }
            assertTrue(File(context.filesDir, LocalPageStore.DIRECTORY).listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun unknownProviderSizeCannotBypassTheStreamingLimit() = runBlocking {
        val source = source("large.html", "text/html", HTML)
        source.outputStream().use { output ->
            output.write(HTML)
            val chunk = ByteArray(64 * 1024) { ' '.code.toByte() }
            repeat(513) { output.write(chunk) }
        }
        provider.reportedSize = null
        val failure = assertThrows(IllegalArgumentException::class.java) { runBlocking { store.importDocument(uri) } }
        assertTrue(failure.message.orEmpty().contains("32 MB"))
        assertTrue(File(context.filesDir, LocalPageStore.DIRECTORY).listFiles().orEmpty().isEmpty())
    }

    @Test fun rejectsFileUrisAndPathsOutsideThePrivateDirectory() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.importDocument(Uri.fromFile(File("/etc/passwd"))) } }
        val sentinel = File(context.filesDir, "sentinel.html").apply { writeBytes(HTML) }
        for (path in listOf("../sentinel.html", "pages/../../sentinel.html", sentinel.absolutePath, "a.html/../sentinel.html", "a%2f.html")) {
            assertNull(store.resolve(path))
            assertFalse(store.delete(path))
            assertThrows(IllegalArgumentException::class.java) { store.restoreBytes(path, HTML) }
        }
        val directory = File(context.filesDir, LocalPageStore.DIRECTORY).apply { mkdirs() }
        Files.createSymbolicLink(File(directory, "linked.html").toPath(), sentinel.toPath())
        assertNull(store.resolve("linked.html"))
        assertArrayEquals(HTML, sentinel.readBytes())
    }

    @Test fun cleanupPreservesCommittedDocumentsAndNewDrafts() {
        val committed = store.restoreBytes("committed.html", HTML)
        val draft = store.restoreBytes("draft.html", HTML)
        val orphan = store.restoreBytes("orphan.html", HTML)
        val protected = store.restoreBytes("protected.html", HTML)
        val yesterday = System.currentTimeMillis() - 48 * 60 * 60 * 1000L
        store.resolve(committed)!!.setLastModified(yesterday)
        store.resolve(orphan)!!.setLastModified(yesterday)
        store.resolve(protected)!!.setLastModified(yesterday)
        val partial = File(context.filesDir, "${LocalPageStore.DIRECTORY}/uncommitted.html.part").apply {
            writeText("in-flight"); setLastModified(yesterday)
        }
        val recentPartial = File(context.filesDir, "${LocalPageStore.DIRECTORY}/recent.html.part").apply { writeText("in-flight") }
        store.cleanup(listOf(BookmarkItem(title = "Offline", url = "", sourceType = BookmarkSourceType.HTML,
            localFileName = committed, localDisplayName = "committed.html")), setOf(protected))
        assertNotNull(store.resolve(committed))
        assertNotNull(store.resolve(draft))
        assertNotNull(store.resolve(protected))
        assertNull(store.resolve(orphan))
        assertFalse(partial.exists())
        assertTrue(recentPartial.exists())
    }

    @Test fun utf16HtmlAndFoldedMimeHeadersAreRecognized() {
        val utf16 = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + "<html><body>中文离线</body></html>".toByteArray(Charsets.UTF_16LE)
        val name = store.restoreBytes("utf16.html", utf16)
        assertEquals("UTF-16LE", LocalPagePolicy.htmlEncoding(store.resolve(name)!!))
        val folded = ARCHIVE.toString(Charsets.UTF_8).replace("multipart/related; boundary=", "multipart/related;\r\n\tboundary=").toByteArray()
        store.validateBytes("folded.mhtml", folded)
    }

    private fun source(name: String, mime: String, bytes: ByteArray): File {
        val file = File(context.cacheDir, "local-page-provider.bin").apply { writeBytes(bytes) }
        provider.file = file; provider.displayName = name; provider.mime = mime; provider.reportedSize = bytes.size.toLong()
        return file
    }

    class DocumentProvider : ContentProvider() {
        lateinit var file: File
        var displayName = "page.html"
        var mime = "text/html"
        var reportedSize: Long? = null
        override fun onCreate() = true
        override fun getType(uri: Uri) = mime
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf(displayName, reportedSize)) }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    companion object {
        private const val AUTHORITY = "test.webshortcuts.local"
        private val uri = Uri.parse("content://$AUTHORITY/document/one")
        private val HTML = "<!doctype html><html><body><h1>Offline</h1></body></html>".toByteArray()
        private val ARCHIVE = ("MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"fixture-boundary\"\r\n\r\n" +
            "--fixture-boundary\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Location: https://offline.example/\r\n\r\n" +
            "<html><body><h1>Local archive</h1></body></html>\r\n--fixture-boundary--\r\n").toByteArray()
    }
}
