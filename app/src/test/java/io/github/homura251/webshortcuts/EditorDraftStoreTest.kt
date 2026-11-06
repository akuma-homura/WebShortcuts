package io.github.homura251.webshortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorDraftStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = EditorDraftStore(context)

    @Test fun originalAndPreviewDraftsAreBothRetainedAndCanRecoverAnAtomicBackup() = runBlocking {
        val id = UUID.randomUUID().toString()
        val original = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val preview = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val originalName = store.write(id, original)
        val previewName = store.write(id, preview)
        val discardedName = store.write(id, preview)
        original.recycle(); preview.recycle()
        val directory = File(context.cacheDir, "editor-drafts")
        val base = File(directory, originalName)
        val backup = File(base.path + ".bak")
        assertTrue(base.renameTo(backup))
        val stale = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        listOf(backup, File(directory, previewName), File(directory, discardedName)).forEach { assertTrue(it.setLastModified(stale)) }
        store.prune(setOf(originalName, previewName))
        val restored = store.read(originalName)!!
        try {
            assertEquals(1600, restored.width)
            assertEquals(900, restored.height)
            assertEquals(Color.CYAN, restored.getPixel(800, 450))
        } finally { restored.recycle() }
        store.read(previewName)!!.recycle()
        assertNull(store.read(discardedName))
        assertNull(store.read("../../other.png"))
    }
}
