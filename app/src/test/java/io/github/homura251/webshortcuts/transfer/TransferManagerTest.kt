package io.github.homura251.webshortcuts.transfer

import android.app.Application
import android.content.Context
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.AppContainer
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.CropRect
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import io.github.homura251.webshortcuts.ui.theme.LayoutMode
import io.github.homura251.webshortcuts.ui.theme.ThemeMode
import io.github.homura251.webshortcuts.ui.theme.ThemePalette
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransferManagerTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val manager get() = container.transferManager

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
        container = AppContainer(context)
    }

    @Test fun zipRoundTripPreservesBothModesOriginalCropSourceTimesAndRawPixels() = runBlocking {
        val custom = seed()
        val generated = BookmarkItem(title = "字母", url = "https://letters.test/", selectedIconSource = IconSourceMode.GENERATED)
        val automatic = BookmarkItem(title = "自动", url = "https://auto.test/")
        container.repository.replaceAll(listOf(custom, generated, automatic))
        val originalBytes = container.iconStore.exportBytes(custom.customOriginalPath!!)!!
        val customBytes = container.iconStore.exportBytes(custom.customImagePath!!)!!
        val autoBytes = container.iconStore.exportBytes(custom.autoImagePath!!)!!
        val appearance = AppearancePreferences(ThemeMode.DARK, false, ThemePalette.OCEAN, LayoutMode.GRID)
        container.themePreferences.update(appearance)
        val backup = backup()
        container.repository.replaceAll(emptyList())
        container.iconStore.cleanup(emptyList())
        container.themePreferences.update(AppearancePreferences())
        val token = token()
        val preview = manager.prepareImport(Uri.fromFile(backup), token) {}
        assertEquals(3, preview.itemCount)
        assertTrue(preview.hasAppearance)
        val result = manager.commitImport(token, ImportMode.REPLACE, true) {}
        assertEquals(3, result.imported)
        val restored = container.repository.snapshot()
        assertEquals(listOf(custom.id, generated.id, automatic.id), restored.map { it.id })
        val actual = restored.first()
        assertEquals(custom.selectedIconSource, actual.selectedIconSource)
        assertEquals(custom.customCrop, actual.customCrop)
        assertEquals(custom.sourceUrl, actual.sourceUrl)
        assertEquals(custom.lastFetchedAt, actual.lastFetchedAt)
        assertEquals(custom.createdAt, actual.createdAt)
        assertEquals(custom.updatedAt, actual.updatedAt)
        assertNotEquals(custom.customImagePath, actual.customImagePath)
        assertArrayEquals(originalBytes, container.iconStore.exportBytes(actual.customOriginalPath!!)!!)
        assertArrayEquals(customBytes, container.iconStore.exportBytes(actual.customImagePath!!)!!)
        assertArrayEquals(autoBytes, container.iconStore.exportBytes(actual.autoImagePath!!)!!)
        assertEquals(appearance, container.themePreferences.state.value)
    }

    @Test fun zipMergeKeepsLocalVersionsAndDistinctCustomizationsAndIsIdempotent() = runBlocking {
        val first = seed()
        val variant = first.copy(id = UUID.randomUUID().toString(), title = "同址另一套图标")
        container.repository.replaceAll(listOf(first, variant))
        val backup = backup()
        val local = first.copy(title = "保留本机名称", imageVersion = 9)
        val localOnly = BookmarkItem(title = "Local", url = "https://local.test/")
        container.repository.replaceAll(listOf(local, localOnly))
        repeat(2) { iteration ->
            val token = token()
            val preview = manager.prepareImport(Uri.fromFile(backup), token) {}
            assertEquals(if (iteration == 0) 1 else 0, preview.newCount)
            val result = manager.commitImport(token, ImportMode.MERGE, false) {}
            assertEquals(if (iteration == 0) 1 else 0, result.imported)
            assertEquals(local, container.repository.find(first.id))
            assertEquals(3, container.repository.snapshot().size)
            assertEquals(variant.id, container.repository.snapshot().first().id)
        }
    }

    @Test fun htmlMergeSkipsExactNormalizedUrlsWithoutTouchingExistingIcons() = runBlocking {
        val original = seed()
        val html = html("<DT><A HREF='https://EXAMPLE.com:443/'>Other title</A>" +
            "<DT><H3>Folder</H3><DL><DT><A HREF='https://example.com/path'>新页面</A></DL>" +
            "<DT><A HREF='javascript:bad()'>Bad</A>")
        val token = token()
        val preview = manager.prepareImport(Uri.fromFile(html), token) {}
        assertEquals(1, preview.newCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(1, preview.skippedCount)
        manager.commitImport(token, ImportMode.MERGE, false) {}
        assertEquals(original, container.repository.find(original.id))
        val added = container.repository.snapshot().first()
        assertEquals("新页面", added.title)
        assertEquals(IconSourceMode.AUTO, added.selectedIconSource)
        assertNull(added.lastFetchedAt)
        assertNull(added.autoImagePath)
    }

    @Test fun invalidOrEmptyHtmlCannotReplaceTheCurrentCollection() = runBlocking {
        val original = seed()
        val token = token()
        val preview = manager.prepareImport(Uri.fromFile(html("<DT><A HREF='about:blank'>Blank</A>")), token) {}
        assertFalse(preview.canReplace)
        assertTrue(runCatching { manager.commitImport(token, ImportMode.REPLACE, false) {} }.isFailure)
        assertEquals(listOf(original), container.repository.snapshot())
    }

    @Test fun metadataFailureRollsBackCollectionAndRemovesOnlyNewUnreferencedImages() = runBlocking {
        val original = seed()
        val backup = backup()
        val local = original.copy(title = "Must survive")
        container.repository.replaceAll(listOf(local))
        val bytesBefore = File(context.filesDir, "bookmarks.json").readBytes()
        val filesBefore = File(context.filesDir, "icons").walkTopDown().filter { it.isFile }.map { it.relativeTo(context.filesDir).path }.toSet()
        val token = token()
        manager.prepareImport(Uri.fromFile(backup), token) {}
        val blocked = File(context.filesDir, "bookmarks.json.new").apply { mkdir() }
        try { assertTrue(runCatching { manager.commitImport(token, ImportMode.REPLACE, false) {} }.isFailure) }
        finally { blocked.deleteRecursively() }
        assertEquals(listOf(local), container.repository.snapshot())
        assertArrayEquals(bytesBefore, File(context.filesDir, "bookmarks.json").readBytes())
        assertEquals(filesBefore, File(context.filesDir, "icons").walkTopDown().filter { it.isFile }.map { it.relativeTo(context.filesDir).path }.toSet())
        assertNotNull(container.iconStore.readOriginal(original.customOriginalPath))
    }

    @Test fun incompleteCacheOrRevokedUriCannotChangeSavedData() = runBlocking {
        val original = seed()
        assertTrue(runCatching { manager.prepareImport(Uri.parse("content://missing.provider/backup"), token()) {} }.isFailure)
        assertTrue(runCatching { manager.restoreImport(token()) {} }.isFailure)
        assertEquals(listOf(original), container.repository.snapshot())
    }

    @Test fun missingSourceImagesAreReportedAndExportedWithoutDanglingReferences() = runBlocking {
        val original = seed()
        File(context.filesDir, original.customImagePath!!).delete()
        val export = manager.prepareExport(TransferFormat.BACKUP_ZIP, token()) {}
        assertEquals(1, export.missingImages)
        val archive = File(context.cacheDir, "transfers/${export.token}/export.zip")
        val token = token()
        val preview = manager.prepareImport(Uri.fromFile(archive), token) {}
        assertTrue(preview.warnings.isNotEmpty())
        manager.commitImport(token, ImportMode.REPLACE, false) {}
        val restored = container.repository.snapshot().single()
        assertNull(restored.customImagePath)
        assertEquals(IconSourceMode.CUSTOM, restored.selectedIconSource)
        assertNotNull(container.iconStore.readOriginal(restored.customOriginalPath))
    }

    @Test fun defaultImportLeavesAppearanceUntouchedAndPreparedCacheCanBeReopened() = runBlocking {
        seed()
        container.themePreferences.update(AppearancePreferences(palette = ThemePalette.ROSE))
        val archive = backup()
        val current = AppearancePreferences(themeMode = ThemeMode.DARK, palette = ThemePalette.AMBER)
        container.themePreferences.update(current)
        val token = token()
        manager.prepareImport(Uri.fromFile(archive), token) {}
        val newManager = TransferManager(context, container.repository, container.iconStore, container.themePreferences,
            container.shortcutPublisher, container.mutationMutex)
        newManager.restoreImport(token) {}
        newManager.commitImport(token, ImportMode.REPLACE, false) {}
        assertEquals(current, container.themePreferences.state.value)
    }

    @Test fun exportCancellationDoesNotMutateTheSourceCollection() = runBlocking {
        val original = seed()
        assertTrue(runCatching { manager.prepareExport(TransferFormat.BACKUP_ZIP, token()) { progress ->
            if (progress.total > 0) throw CancellationException("cancel")
        } }.exceptionOrNull() is CancellationException)
        assertEquals(listOf(original), container.repository.snapshot())
        assertNotNull(container.iconStore.read(original.customImagePath))
    }

    @Test fun documentOutputIsClosedBeforeExportIsReportedAsComplete() = runBlocking {
        val original = seed()
        val export = manager.prepareExport(TransferFormat.BOOKMARKS_HTML, token()) {}
        val uri = Uri.parse("content://transfer.test/created-document")
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        Shadows.shadowOf(context.contentResolver).registerOutputStream(uri, output)
        val result = manager.writeExport(export.token, uri) {}
        assertTrue(closed)
        assertEquals(1, result.itemCount)
        assertEquals(original.title, BookmarkHtmlCodec.decode(output.toByteArray()).items.single().title)
        assertEquals(original, container.repository.snapshot().single())
    }

    @Test fun providerFailureOnCloseIsAnExportFailureAndLeavesCollectionUntouched() = runBlocking {
        val original = seed()
        val export = manager.prepareExport(TransferFormat.BACKUP_ZIP, token()) {}
        val uri = Uri.parse("content://transfer.test/failing-document")
        val output = object : ByteArrayOutputStream() {
            override fun close() { throw IOException("Provider failed to commit") }
        }
        Shadows.shadowOf(context.contentResolver).registerOutputStream(uri, output)
        assertTrue(runCatching { manager.writeExport(export.token, uri) {} }.exceptionOrNull() is IOException)
        assertEquals(original, container.repository.snapshot().single())
        assertNotNull(container.iconStore.read(original.customImagePath))
    }

    @Test fun emptyZipCanRestoreAnEmptyCollectionAfterExplicitReplace() = runBlocking {
        val empty = backup()
        seed()
        val token = token()
        val preview = manager.prepareImport(Uri.fromFile(empty), token) {}
        assertTrue(preview.canReplace)
        manager.commitImport(token, ImportMode.REPLACE, false) {}
        assertTrue(container.repository.snapshot().isEmpty())
    }

    @Test fun replacementReenablesRetainedDesktopIdsAndDisablesRemovedIds() = runBlocking {
        val original = seed()
        val backup = backup()
        val removed = BookmarkItem(title = "Removed", url = "https://removed.test/", selectedIconSource = IconSourceMode.GENERATED)
        container.repository.save(removed)
        val platform = context.getSystemService(ShortcutManager::class.java)
        Shadows.shadowOf(platform).setIsRequestPinShortcutSupported(true)
        assertTrue(container.shortcutPublisher.pin(original))
        assertTrue(container.shortcutPublisher.pin(removed))
        container.shortcutPublisher.disable(original.id)
        val token = token()
        manager.prepareImport(Uri.fromFile(backup), token) {}
        manager.commitImport(token, ImportMode.REPLACE, false) {}
        val pinned = platform.pinnedShortcuts.associateBy { it.id }
        // Robolectric 4.15 tracks disabled IDs separately; ShortcutInfo.isEnabled is not updated.
        val disabled = disabledPinnedIds()
        assertFalse(original.id in disabled)
        assertTrue(removed.id in disabled)
        assertEquals(original.title, pinned.getValue(original.id).shortLabel.toString())
    }

    @Test fun unchangedMergeDoesNotReenableOrRewriteExistingDesktopEntries() = runBlocking {
        val original = seed()
        val backup = backup()
        Shadows.shadowOf(context.getSystemService(ShortcutManager::class.java)).setIsRequestPinShortcutSupported(true)
        assertTrue(container.shortcutPublisher.pin(original))
        container.shortcutPublisher.disable(original.id)
        val token = token()
        manager.prepareImport(Uri.fromFile(backup), token) {}
        val result = manager.commitImport(token, ImportMode.MERGE, false) {}
        assertEquals(0, result.imported)
        assertEquals(original, container.repository.find(original.id))
        assertTrue(original.id in disabledPinnedIds())
    }

    @Test fun aLegacyWebpFilenameWithValidPngContentStillSurvivesBackup() = runBlocking {
        val original = seed()
        val source = File(context.filesDir, original.customImagePath!!)
        val bytes = source.readBytes()
        val legacy = source.path.removeSuffix("png") + "webp"
        source.copyTo(File(legacy))
        val old = original.copy(customImagePath = File(legacy).relativeTo(context.filesDir).path)
        container.repository.save(old)
        val export = manager.prepareExport(TransferFormat.BACKUP_ZIP, token()) {}
        assertEquals(0, export.missingImages)
        val token = token()
        manager.prepareImport(Uri.fromFile(File(context.cacheDir, "transfers/${export.token}/export.zip")), token) {}
        manager.commitImport(token, ImportMode.REPLACE, false) {}
        assertArrayEquals(bytes, container.iconStore.exportBytes(container.repository.snapshot().single().customImagePath!!)!!)
    }

    private suspend fun seed(): BookmarkItem {
        val id = UUID.randomUUID().toString()
        val auto = bitmap(512, 512, Color.RED)
        val custom = bitmap(512, 512, Color.BLUE)
        val original = bitmap(1000, 600, Color.GREEN)
        try {
            val item = BookmarkItem(id = id, title = "自定义收藏", url = "https://example.com/", selectedIconSource = IconSourceMode.CUSTOM,
                autoImagePath = container.iconStore.save(id, 1, IconSourceMode.AUTO, auto),
                customImagePath = container.iconStore.save(id, 1, IconSourceMode.CUSTOM, custom),
                customOriginalPath = container.iconStore.saveOriginal(id, 1, original), customCrop = CropRect(0.1f, 0.2f, 0.8f, 0.9f),
                sourceUrl = "https://example.com/icon.png", lastFetchedAt = 1_700_000_000_000,
                createdAt = 1_600_000_000_000, updatedAt = 1_700_000_000_000)
            container.repository.save(item)
            return item
        } finally { auto.recycle(); custom.recycle(); original.recycle() }
    }

    private suspend fun backup(): File {
        val export = manager.prepareExport(TransferFormat.BACKUP_ZIP, token()) {}
        return File(context.cacheDir, "transfers/${export.token}/export.zip")
    }
    private fun token() = UUID.randomUUID().toString()
    private fun disabledPinnedIds(): Set<String> = ReflectionHelpers.getStaticField<Map<String, android.content.pm.ShortcutInfo>>(
        ShadowShortcutManager::class.java, "disabledPinnedShortcuts").keys
    private fun bitmap(width: Int, height: Int, color: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun html(body: String) = File(context.cacheDir, "browser-${token()}.html").apply {
        writeText("<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>$body</DL>")
    }
}
