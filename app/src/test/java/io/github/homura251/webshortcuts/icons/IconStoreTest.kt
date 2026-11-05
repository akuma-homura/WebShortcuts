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

    @Test fun customModeNeverResolvesAnAutoImage() = runBlocking {
        val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val auto = store.save("test-custom", 1, IconSourceMode.AUTO, image)
        val bookmark = BookmarkItem(id = "test-custom", title = "Web", url = "https://example.com",
            selectedIconSource = IconSourceMode.CUSTOM, autoImagePath = auto, customImagePath = "icons/custom/missing.png")
        val result = IconResolver(context, store, processor).resolve(bookmark)
        assertNotEquals(Color.GREEN, result.getPixel(0, 0))
    }
}
