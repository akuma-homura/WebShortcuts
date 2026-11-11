package io.github.homura251.webshortcuts.transfer

import io.github.homura251.webshortcuts.data.BookmarkCodec
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupLocalPagesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun archivePreservesBrowserSelectionAndBothDocumentFormats() {
        val web = BookmarkItem(title = "Work account", url = "https://example.com/", browserPackage = "com.chrome.beta")
        val html = local("document.html", BookmarkSourceType.HTML)
        val mhtml = local("archive.mhtml", BookmarkSourceType.MHTML)
        val htmlFile = temporary.newFile().apply { writeText("<!doctype html><html><body>Offline</body></html>") }
        val mhtmlFile = temporary.newFile().apply { writeText("Saved MIME archive bytes") }
        val archive = temporary.newFile()
        BackupArchive.write(archive, listOf(web, html, mhtml), AppearancePreferences(),
            mapOf("pages/document.html" to htmlFile, "pages/archive.mhtml" to mhtmlFile), "2.0.0", 0)
        val (parsed, assets) = BackupArchive.read(archive, temporary.newFolder())
        assertEquals(listOf(web, html, mhtml), parsed.items)
        assertArrayEquals(htmlFile.readBytes(), assets.getValue("pages/document.html").readBytes())
        assertArrayEquals(mhtmlFile.readBytes(), assets.getValue("pages/archive.mhtml").readBytes())
    }

    @Test fun missingLocalContentAndEscapingArchivePathsFailClosed() {
        val page = local("document.html", BookmarkSourceType.HTML)
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.write(temporary.newFile(), listOf(page), AppearancePreferences(), emptyMap(), "2.0.0", 0)
        }
        assertFalse(BackupArchive.isAssetPath("pages/../bookmarks.json"))
        assertFalse(BackupArchive.isAssetPath("pages/nested/index.html"))
        assertFalse(BackupArchive.isAssetPath("pages/absolute.mht"))
        val archive = temporary.newFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            mapOf("manifest.json" to JSONObject().put("format", "WebShortcutsBackup").put("formatVersion", 2).toString(),
                "bookmarks.json" to BookmarkCodec.encode(listOf(page))).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes.toByteArray()); zip.closeEntry()
            }
        }
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.read(archive, temporary.newFolder()) }
    }

    @Test fun browserBookmarkHtmlContainsOnlyOnlineLinks() {
        val page = local("document.html", BookmarkSourceType.HTML)
        val web = BookmarkItem(title = "Online", url = "https://example.com/", browserPackage = "org.mozilla.firefox")
        val html = BookmarkHtmlCodec.encode(listOf(page, web)).toString(Charsets.UTF_8)
        assertTrue(html.contains("HREF=\"https://example.com/\""))
        assertFalse(html.contains("HREF=\"\""))
        assertFalse(html.contains("document.html"))
        assertEquals(1, BookmarkHtmlCodec.decode(html.toByteArray()).items.size)
    }

    private fun local(name: String, type: BookmarkSourceType) = BookmarkItem(title = "Local", url = "",
        sourceType = type, localFileName = name, localDisplayName = name)
}
