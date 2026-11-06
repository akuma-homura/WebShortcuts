package io.github.homura251.webshortcuts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkRepository
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** Exercise real original/preview files across editor cancellation and process-style restoration. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = WebShortcutsApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomCropViewModelTest {
    private lateinit var application: WebShortcutsApplication
    private val stores = mutableListOf<ViewModelStore>()
    private val rightHalf = CropRect(0.5f, 0f, 1f, 1f)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        application.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        application.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @After fun tearDown() {
        clearViewModels()
        Dispatchers.resetMain()
    }

    @Test fun savedOffCenterCropCanBeEditedAgainFromTheRetainedOriginalAfterReload() = runBlocking {
        val seeded = seedEditable()
        val vm = createViewModel()
        vm.edit(seeded)
        awaitEditable(vm)
        vm.applyCustomCrop(rightHalf)
        awaitCrop(vm, rightHalf)
        vm.save()
        awaitEditorClosed(vm)
        val saved = fromDisk(seeded.id)
        assertEquals(rightHalf, saved.customCrop)
        assertEquals(seeded.customOriginalPath, saved.customOriginalPath)
        val normalized = application.container.iconStore.read(saved.customImagePath)!!
        try { assertEquals(Color.BLUE, normalized.getPixel(256, 256)) } finally { normalized.recycle() }

        clearViewModels()
        val reopened = createViewModel()
        reopened.edit(saved)
        awaitEditable(reopened)
        val source = reopened.state.value.editor!!.cropSource!!
        assertEquals(400, source.width)
        assertEquals(200, source.height)
        assertEquals(Color.RED, source.getPixel(20, 100))
        assertEquals(Color.BLUE, source.getPixel(380, 100))
        val leftHalf = CropRect(0f, 0f, 0.5f, 1f)
        reopened.applyCustomCrop(leftHalf)
        awaitCrop(reopened, leftHalf)
        reopened.save()
        awaitEditorClosed(reopened)
        val recropped = fromDisk(seeded.id)
        assertEquals(leftHalf, recropped.customCrop)
        val red = application.container.iconStore.read(recropped.customImagePath)!!
        try { assertEquals(Color.RED, red.getPixel(256, 256)) } finally { red.recycle() }
    }

    @Test fun cancellingAnAppliedCropLeavesSavedMetadataAndOriginalPixelsUntouched() = runBlocking {
        val seeded = seedEditable()
        val vm = createViewModel()
        vm.edit(seeded)
        awaitEditable(vm)
        val metadata = File(application.filesDir, "bookmarks.json")
        val before = metadata.readBytes()
        vm.applyCustomCrop(rightHalf)
        awaitCrop(vm, rightHalf)
        vm.dismissEditor()
        awaitEditorClosed(vm)
        assertArrayEquals(before, metadata.readBytes())
        assertEquals(seeded, fromDisk(seeded.id))
        val source = application.container.iconStore.readOriginal(seeded.customOriginalPath)!!
        try {
            assertEquals(Color.RED, source.getPixel(20, 100))
            assertEquals(Color.BLUE, source.getPixel(380, 100))
        } finally { source.recycle() }
    }

    @Test fun originalAndCropDraftRestoreAndSaveWithoutOpeningPhotoPickerAgain() = runBlocking {
        val id = UUID.randomUUID().toString()
        val source = twoColorOriginal()
        val preview = application.container.iconProcessor.crop(source, rightHalf)
        val drafts = EditorDraftStore(application)
        val sourceName = drafts.write(id, source)
        val previewName = drafts.write(id, preview)
        source.recycle(); preview.recycle()
        val handle = SavedStateHandle(mapOf(
            "draft.id" to id, "draft.new" to true, "draft.title" to "Restored crop",
            "draft.url" to "https://example.com/", "draft.mode" to "CUSTOM", "draft.dirty" to true,
            "draft.custom" to previewName, "draft.original" to sourceName,
            "draft.crop" to floatArrayOf(0.5f, 0f, 1f, 1f),
        ))
        val vm = createViewModel(handle)
        awaitEditable(vm)
        assertEquals(rightHalf, vm.state.value.editor!!.cropRect)
        assertEquals(400, vm.state.value.editor!!.cropSource!!.width)
        vm.save()
        awaitEditorClosed(vm)
        val saved = fromDisk(id)
        assertEquals(rightHalf, saved.customCrop)
        assertNotNull(saved.customOriginalPath)
        assertNotNull(saved.customImagePath)
        val original = application.container.iconStore.readOriginal(saved.customOriginalPath)!!
        try {
            assertEquals(400, original.width)
            assertEquals(Color.RED, original.getPixel(20, 100))
        } finally { original.recycle() }
        assertNull(handle.get<String>("draft.original"))
    }

    @Test fun clearingCropStaysClearedThroughDraftRestorationAndSave() = runBlocking {
        val seeded = seedEditable(rightHalf)
        val handle = SavedStateHandle()
        val vm = createViewModel(handle)
        vm.edit(seeded)
        awaitEditable(vm)
        vm.applyCustomCrop(null)
        awaitCrop(vm, null)
        assertTrue(handle.contains("draft.crop"))
        assertNull(handle.get<FloatArray>("draft.crop"))
        clearViewModels()
        val restored = createViewModel(handle)
        awaitEditable(restored)
        assertNull(restored.state.value.editor!!.cropRect)
        restored.save()
        awaitEditorClosed(restored)
        val saved = fromDisk(seeded.id)
        assertNull(saved.customCrop)
        assertEquals(seeded.customOriginalPath, saved.customOriginalPath)
        val full = application.container.iconStore.read(saved.customImagePath)!!
        try {
            assertEquals(Color.RED, full.getPixel(200, 256))
            assertEquals(Color.BLUE, full.getPixel(312, 256))
        } finally { full.recycle() }
    }

    @Test fun missingNewOriginalDraftCannotReuseThePreviousBookmarksDifferentOriginal() = runBlocking {
        val seeded = seedEditable()
        val preview = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val name = EditorDraftStore(application).write(seeded.id, preview)
        preview.recycle()
        val missing = "${seeded.id}_${UUID.randomUUID()}.png"
        val handle = SavedStateHandle(mapOf(
            "draft.id" to seeded.id, "draft.new" to false, "draft.title" to seeded.title,
            "draft.url" to seeded.url, "draft.mode" to "CUSTOM", "draft.dirty" to true,
            "draft.custom" to name, "draft.original" to missing,
        ))
        val vm = createViewModel(handle)
        assertFalse(vm.state.value.editor!!.canCrop)
        assertNull(vm.state.value.editor!!.cropSource)
        vm.save()
        assertNotNull("Saving must wait for a replacement original or discarding the edit", vm.state.value.editor)
        assertNotNull(vm.state.value.editor!!.error)
        assertEquals(seeded, fromDisk(seeded.id))
    }

    @Test fun missingPreviewDraftIsRebuiltFromItsRetainedOriginalAndCrop() = runBlocking {
        val id = UUID.randomUUID().toString()
        val source = twoColorOriginal()
        val sourceName = EditorDraftStore(application).write(id, source)
        source.recycle()
        val missingPreview = "${id}_${UUID.randomUUID()}.png"
        val handle = SavedStateHandle(mapOf(
            "draft.id" to id, "draft.new" to true, "draft.title" to "Recovered preview",
            "draft.url" to "https://example.com/", "draft.mode" to "CUSTOM", "draft.dirty" to true,
            "draft.custom" to missingPreview, "draft.original" to sourceName,
            "draft.crop" to floatArrayOf(0.5f, 0f, 1f, 1f),
        ))
        val vm = createViewModel(handle)
        awaitEditable(vm)
        assertTrue(vm.state.value.editor!!.hasCustomImage)
        assertEquals(rightHalf, vm.state.value.editor!!.cropRect)
        assertNull(vm.state.value.editor!!.error)
        val rebuilt = handle.get<String>("draft.custom")
        assertNotNull(rebuilt)
        assertNotEquals(missingPreview, rebuilt)
        val preview = EditorDraftStore(application).read(rebuilt)!!
        try { assertEquals(Color.BLUE, preview.getPixel(256, 256)) } finally { preview.recycle() }
        vm.save()
        awaitEditorClosed(vm)
        val saved = fromDisk(id)
        assertEquals(rightHalf, saved.customCrop)
        assertNotNull(saved.customOriginalPath)
        val normalized = application.container.iconStore.read(saved.customImagePath)!!
        try { assertEquals(Color.BLUE, normalized.getPixel(256, 256)) } finally { normalized.recycle() }
    }

    private suspend fun seedEditable(crop: CropRect? = null): BookmarkItem {
        val source = twoColorOriginal()
        val preview = application.container.iconProcessor.crop(source, crop)
        val item = BookmarkItem(title = "Editable", url = "https://example.com/", selectedIconSource = IconSourceMode.CUSTOM,
            customCrop = crop, createdAt = 1L, updatedAt = 1L)
        return try {
            val originalPath = application.container.iconStore.saveOriginal(item.id, 1, source)
            val previewPath = application.container.iconStore.save(item.id, 1, IconSourceMode.CUSTOM, preview)
            item.copy(customOriginalPath = originalPath, customImagePath = previewPath)
                .also { application.container.repository.save(it) }
        } finally { source.recycle(); preview.recycle() }
    }

    private fun twoColorOriginal() = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(200f, 0f, 400f, 200f, Paint().apply { color = Color.BLUE })
        }
    }

    private suspend fun fromDisk(id: String): BookmarkItem = BookmarkRepository(application).let { repository ->
        repository.load()
        checkNotNull(repository.find(id))
    }

    private suspend fun createViewModel(handle: SavedStateHandle = SavedStateHandle()): MainViewModel {
        val store = ViewModelStore().also(stores::add)
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application, handle) as T
        })
        return provider[MainViewModel::class.java].also { vm -> withTimeout(5_000) { vm.state.first { !it.loading } } }
    }

    private suspend fun awaitEditable(vm: MainViewModel) {
        withTimeout(5_000) { vm.state.first { it.editor?.canCrop == true && it.editor.cropSource != null } }
    }

    private suspend fun awaitCrop(vm: MainViewModel, rect: CropRect?) {
        withTimeout(5_000) { vm.state.first { it.editor?.loading == false && it.editor.cropRect == rect } }
    }

    private suspend fun awaitEditorClosed(vm: MainViewModel) {
        withTimeout(5_000) { vm.state.first { it.editor == null } }
    }

    private fun clearViewModels() {
        stores.forEach(ViewModelStore::clear)
        stores.clear()
    }
}
