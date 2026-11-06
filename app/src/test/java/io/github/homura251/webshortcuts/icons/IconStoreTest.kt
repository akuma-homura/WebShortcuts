package io.github.homura251.webshortcuts.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = IconStore(context)
    private val processor = IconProcessor()

    @Test fun cleanupRetainsCustomHistoryWhenAutoIsSelected() = runBlocking {
        val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val obsolete = store.save("test-site", 1, IconSourceMode.AUTO, image)
        val auto = store.save("test-site", 2, IconSourceMode.AUTO, image)
        val custom = store.save("test-site", 3, IconSourceMode.CUSTOM, image)
        val bookmark = BookmarkItem(id = "test-site", title = "Example", url = "https://example.com",
            autoImagePath = auto, customImagePath = custom, selectedIconSource = IconSourceMode.AUTO)
        store.cleanup(listOf(bookmark))
        assertFalse(File(context.filesDir, obsolete).exists())
        assertNotNull(store.read(auto))
        assertNotNull(store.read(custom))
        store.cleanup(emptyList())
        assertNull(store.read(auto))
        assertNull(store.read(custom))
    }

    @Test fun pathTraversalCannotReadOrDeleteFilesOutsideIconStore() = runBlocking {
        val sentinel = File(context.filesDir, "sentinel.txt").apply { writeText("keep") }
        assertNull(store.read("../sentinel.txt"))
        assertNull(store.read("icons/auto/../../sentinel.txt"))
        assertNull(store.read(sentinel.absolutePath))
        store.cleanup(emptyList())
        assertEquals("keep", sentinel.readText())
    }

    @Test fun cleanupPreservesAndReadRecoversCommittedAtomicBackup() = runBlocking {
        val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val path = store.save("interrupted-write", 1, IconSourceMode.CUSTOM, image)
        val base = File(context.filesDir, path)
        val backup = File(base.path + ".bak")
        assertTrue(base.renameTo(backup))
        // Simulate the legacy AtomicFile state after a process dies during a write.
        base.writeText("incomplete image")
        val bookmark = BookmarkItem(id = "interrupted-write", title = "Example", url = "https://example.com",
            customImagePath = path, selectedIconSource = IconSourceMode.CUSTOM)
        store.cleanup(listOf(bookmark))
        assertTrue(backup.exists())
        val recovered = store.read(path)
        assertNotNull(recovered)
        assertEquals(Color.BLUE, recovered!!.getPixel(256, 256))
        assertFalse(backup.exists())
    }

    @Test fun atomicBackupSurvivesCleanupWhenBaseIsMissing() = runBlocking {
        val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val path = store.save("missing-base", 1, IconSourceMode.AUTO, image)
        val base = File(context.filesDir, path)
        val backup = File(base.path + ".bak")
        assertTrue(base.renameTo(backup))
        val bookmark = BookmarkItem(id = "missing-base", title = "Example", url = "https://example.com", autoImagePath = path)
        store.cleanup(listOf(bookmark))
        assertNotNull(store.read(path))
        assertTrue(base.exists())
    }

    @Test fun customModeNeverResolvesAnAutoImage() = runBlocking {
        val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val auto = store.save("test-custom", 1, IconSourceMode.AUTO, image)
        val bookmark = BookmarkItem(id = "test-custom", title = "Web", url = "https://example.com",
            selectedIconSource = IconSourceMode.CUSTOM, autoImagePath = auto, customImagePath = "icons/custom/missing.png")
        val result = IconResolver(context, store, processor).resolve(bookmark)
        assertNotEquals(Color.GREEN, result.getPixel(0, 0))
    }

    @Test fun editableOriginalKeepsItsPixelsAndSurvivesInactiveCustomModeCleanup() = runBlocking {
        val original = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val obsolete = store.saveOriginal("editable", 1, original)
        val current = store.saveOriginal("editable", 2, original)
        val bookmark = BookmarkItem(id = "editable", title = "Example", url = "https://example.com/",
            selectedIconSource = IconSourceMode.AUTO, customOriginalPath = current, customCrop = CropRect(0.1f, 0.2f, 0.7f, 0.9f))
        store.cleanup(listOf(bookmark))
        assertNull(store.readOriginal(obsolete))
        val restored = store.readOriginal(current)!!
        try {
            assertEquals(1600, restored.width)
            assertEquals(900, restored.height)
            assertEquals(Color.GREEN, restored.getPixel(100, 100))
            assertNull(store.read(current)) // The launcher resolver cannot confuse original and normalized images.
        } finally { restored.recycle(); original.recycle() }
        store.cleanup(emptyList())
        assertNull(store.readOriginal(current))
    }

    @Test fun originalAtomicBackupIsRecoveredAndUnsafeOrOversizedOriginalsAreRejected() = runBlocking {
        val image = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val path = store.saveOriginal("original-backup", 1, image)
        val base = File(context.filesDir, path)
        assertTrue(base.renameTo(File(base.path + ".bak")))
        val bookmark = BookmarkItem(id = "original-backup", title = "Example", url = "https://example.com/", customOriginalPath = path)
        store.cleanup(listOf(bookmark))
        val restored = store.readOriginal(path)!!
        try { assertEquals(Color.BLUE, restored.getPixel(512, 384)) } finally { restored.recycle(); image.recycle() }
        assertNull(store.readOriginal("icons/original/../../sentinel.txt"))
        val huge = Bitmap.createBitmap(2049, 1, Bitmap.Config.ARGB_8888)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.saveOriginal("oversized", 1, huge) }
            }
        } finally { huge.recycle() }
        Unit
    }
}
