package io.github.homura251.webshortcuts.transfer

import io.github.homura251.webshortcuts.data.BookmarkCodec
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun archiveEnvelopePreservesMetadataAndRawReferencedAssets() {
        val path = "icons/auto/source.png"
        val item = BookmarkItem(title = "Site", url = "https://example.com/", autoImagePath = path, lastFetchedAt = 1234,
            sourceUrl = "https://example.com/icon.png")
        val asset = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val archive = temporary.newFile()
        BackupArchive.write(archive, listOf(item), AppearancePreferences(), mapOf(path to asset), "1.2.0", 0)
        val (parsed, assets) = BackupArchive.read(archive, temporary.newFolder())
        assertEquals(listOf(item), parsed.items)
        assertEquals(AppearancePreferences(), parsed.appearance)
        assertArrayEquals(asset.readBytes(), assets.getValue(path).readBytes())
        assertNotNull(parsed.exportedAt)
    }

    @Test fun unknownVersionsMissingReferencesAndExtraFilesFailClosed() {
        assertRejected(base().apply { this["manifest.json"] = manifest(99) })
        val item = BookmarkItem(title = "Site", url = "https://example.com/", customImagePath = "icons/custom/missing.png")
        assertRejected(base().apply { this["bookmarks.json"] = BookmarkCodec.encode(listOf(item)).toByteArray() })
        assertRejected(base().apply { this["icons/auto/extra.png"] = byteArrayOf(1) })
        assertRejected(base().apply { remove("bookmarks.json") })
    }

    @Test fun invalidImagePathsCannotBeSilentlyDroppedByTheLegacyCodec() {
        val json = JSONObject(BookmarkCodec.encode(listOf(BookmarkItem(title = "Site", url = "https://example.com/"))))
        json.getJSONArray("bookmarks").getJSONObject(0).put("autoImagePath", "../../outside.png")
        assertRejected(base().apply { this["bookmarks.json"] = json.toString().toByteArray() })
        assertRejected(base().apply { this["../escape.txt"] = byteArrayOf(1) })
        assertRejected(base().apply { this["/absolute.txt"] = byteArrayOf(1) })
        assertRejected(base().apply { this["icons/auto/../escape.png"] = byteArrayOf(1) })
    }

    @Test fun invalidAppearanceIsReportedWithoutDiscardingValidBookmarks() {
        val archive = zip(base().apply { this["appearance.json"] = "{\"themeMode\":\"FUTURE\"}".toByteArray() })
        val parsed = BackupArchive.read(archive, temporary.newFolder()).first
        assertNull(parsed.appearance)
        assertEquals(1, parsed.warnings.size)
    }

    @Test fun actualCrcIsVerifiedInsteadOfTrustingZipMetadata() {
        val archive = zip(base(), stored = true)
        val bytes = archive.readBytes()
        val needle = "WebShortcutsBackup".toByteArray()
        val offset = (0..bytes.size - needle.size).first { start -> needle.indices.all { bytes[start + it] == needle[it] } }
        bytes[offset] = 'X'.code.toByte()
        archive.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.read(archive, temporary.newFolder()) }
    }

    @Test fun duplicateCentralDirectoryNamesAreRejected() {
        val data = base().apply { this["manifesx.json"] = this.getValue("manifest.json") }
        val archive = zip(data, stored = true)
        val bytes = archive.readBytes()
        val from = "manifesx.json".toByteArray()
        val to = "manifest.json".toByteArray()
        for (start in 0..bytes.size - from.size) if (from.indices.all { bytes[start + it] == from[it] }) to.copyInto(bytes, start)
        archive.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.read(archive, temporary.newFolder()) }
    }

    @Test fun archiveAndEntryLimitsApplyBeforeExtraction() {
        val large = temporary.newFile()
        RandomAccessFile(large, "rw").use { it.setLength(BackupArchive.MAX_ARCHIVE_BYTES + 1) }
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.read(large, temporary.newFolder()) }
        val many = zip((0..BackupArchive.MAX_ENTRIES).associate { "icons/auto/$it.png" to byteArrayOf() })
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.read(many, temporary.newFolder()) }
    }

    @Test fun highlyCompressedImagesAreBoundedByActualExpandedBytes() {
        val archive = zip(base().apply { this["icons/auto/large.png"] = ByteArray(5 * 1024 * 1024 + 1) })
        assertTrue(archive.length() < 20_000)
        val failure = assertThrows(java.io.IOException::class.java) { BackupArchive.read(archive, temporary.newFolder()) }
        assertTrue(failure.message!!.contains("limit"))
    }

    private fun base() = linkedMapOf("manifest.json" to manifest(), "bookmarks.json" to BookmarkCodec.encode(emptyList()).toByteArray())
    private fun manifest(version: Int = 1) = JSONObject().put("format", "WebShortcutsBackup").put("formatVersion", version).toString().toByteArray()
    private fun assertRejected(entries: Map<String, ByteArray>) {
        assertThrows(Exception::class.java) { BackupArchive.read(zip(entries), temporary.newFolder()) }
    }

    private fun zip(entries: Map<String, ByteArray>, stored: Boolean = false): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { out -> entries.forEach { (name, bytes) ->
            val entry = ZipEntry(name)
            if (stored) { entry.method = ZipEntry.STORED; entry.size = bytes.size.toLong(); entry.crc = CRC32().apply { update(bytes) }.value }
            out.putNextEntry(entry); out.write(bytes); out.closeEntry()
        } }
    }
}
