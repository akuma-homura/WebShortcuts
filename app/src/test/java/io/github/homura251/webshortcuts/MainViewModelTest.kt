package io.github.homura251.webshortcuts

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real files and real coroutine boundaries exercise the editor-to-repository transaction. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = WebShortcutsApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainViewModelTest {
    private lateinit var application: WebShortcutsApplication
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        application.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        application.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @After fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test fun `switching custom to generated and auto preserves the inactive custom image`() = runBlocking {
        val original = seedBookmark(IconSourceMode.CUSTOM)
        val vm = createViewModel()
        vm.edit(original)
        vm.setMode(IconSourceMode.GENERATED)
        vm.save()
        awaitEditorClosed(vm)
        val generated = application.container.repository.find(original.id)!!
        assertEquals(IconSourceMode.GENERATED, generated.selectedIconSource)
        assertEquals(original.customImagePath, generated.customImagePath)
        assertNotNull(application.container.iconStore.read(generated.customImagePath))

        vm.edit(generated)
        vm.setMode(IconSourceMode.AUTO) // Existing AUTO cache makes this a local mode change.
        vm.save()
        awaitEditorClosed(vm)
        val automatic = application.container.repository.find(original.id)!!
        assertEquals(IconSourceMode.AUTO, automatic.selectedIconSource)
        assertEquals(original.customImagePath, automatic.customImagePath)
        assertEquals(original.autoImagePath, automatic.autoImagePath)
        assertEquals(Color.BLUE, application.container.iconStore.read(automatic.customImagePath)!!.getPixel(256, 256))
    }

    @Test fun `changing a custom bookmark URL clears stale automatic metadata and retains its custom image`() = runBlocking {
        val original = seedBookmark(IconSourceMode.CUSTOM)
        val vm = createViewModel()
        vm.edit(original)
        vm.setUrl("https://new.example/destination")
        vm.onUrlFocusLost()
        assertFalse(vm.state.value.editor!!.loading)
        vm.save()
        awaitEditorClosed(vm)
        val updated = application.container.repository.find(original.id)!!
        assertEquals("https://new.example/destination", updated.url)
        assertEquals(IconSourceMode.CUSTOM, updated.selectedIconSource)
        assertEquals(original.customImagePath, updated.customImagePath)
        assertNull(updated.autoImagePath)
        assertNull(updated.sourceUrl)
        assertNull(updated.lastFetchedAt)
        assertEquals(Color.BLUE, application.container.iconStore.read(updated.customImagePath)!!.getPixel(256, 256))
    }

    @Test fun `saving an editor snapshot preserves a newer background icon version`() = runBlocking {
        val original = seedBookmark(IconSourceMode.AUTO)
        val vm = createViewModel()
        vm.edit(original)
        val container = application.container
        val newer = container.mutationMutex.withLock {
            val path = container.iconStore.save(original.id, 2, IconSourceMode.AUTO, bitmap(Color.GREEN))
            original.copy(autoImagePath = path, sourceUrl = "https://example.com/new.png", imageVersion = 2)
                .also { container.repository.save(it) }
        }
        vm.setTitle("Renamed while refreshing")
        vm.save()
        awaitEditorClosed(vm)
        val updated = container.repository.find(original.id)!!
        assertEquals("Renamed while refreshing", updated.title)
        assertEquals(newer.autoImagePath, updated.autoImagePath)
        assertEquals(newer.sourceUrl, updated.sourceUrl)
        assertEquals(original.customImagePath, updated.customImagePath)
        assertEquals(3, updated.imageVersion)
        assertEquals(Color.GREEN, container.iconStore.read(updated.autoImagePath)!!.getPixel(256, 256))
    }

    @Test fun `restored custom image draft can be saved without requesting photo access again`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val draftName = EditorDraftStore(application).write(id, bitmap(Color.MAGENTA))
        val handle = SavedStateHandle(mapOf(
            "draft.id" to id, "draft.new" to true, "draft.title" to "Restored",
            "draft.url" to "https://example.com/", "draft.mode" to "CUSTOM",
            "draft.dirty" to true, "draft.custom" to draftName,
        ))
        val vm = createViewModel(handle)
        withTimeout(5_000) { vm.state.first { it.editor?.hasCustomImage == true } }
        assertEquals(id, vm.state.value.editor?.id)
        assertEquals(IconSourceMode.CUSTOM, vm.state.value.editor?.mode)
        vm.save()
        awaitEditorClosed(vm)
        val saved = application.container.repository.find(id)!!
        assertEquals(IconSourceMode.CUSTOM, saved.selectedIconSource)
        assertNotNull(saved.customImagePath)
        assertEquals(Color.MAGENTA, application.container.iconStore.read(saved.customImagePath)!!.getPixel(256, 256))
        assertNull(handle.get<String>("draft.id"))
    }

    @Test fun `unreadable metadata prevents new bookmarks and preserves the original bytes`() = runBlocking {
        val metadata = File(application.filesDir, "bookmarks.json")
        val damaged = "{the original file must not be replaced"
        metadata.writeText(damaged)
        val vm = createViewModel()
        withTimeout(5_000) { vm.state.first { it.error != null } }
        vm.add()
        assertNull(vm.state.value.editor)
        assertTrue(vm.state.value.bookmarks.isEmpty())
        assertEquals(damaged, metadata.readText())
    }

    @Test fun `saving during automatic discovery persists immediately and finishes the icon in background`() = runBlocking {
        val server = MockWebServer()
        val iconRequested = CountDownLatch(1)
        val allowIconResponse = CountDownLatch(1)
        val greenPng = ByteArrayOutputStream().also { bitmap(Color.GREEN).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> MockResponse().setHeader("Content-Type", "text/html")
                    .setBody("""<head><link rel="icon" sizes="192x192" href="/icon.png"></head>""")
                "/icon.png" -> {
                    iconRequested.countDown()
                    // Both the cancelled editor request and its background replacement are held
                    // until the test has observed the committed bookmark and closed editor.
                    if (allowIconResponse.await(5, TimeUnit.SECONDS)) {
                        MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(greenPng))
                    } else MockResponse().setResponseCode(503)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val vm = createViewModel()
            val url = server.url("/site").toString()
            vm.add()
            vm.setTitle("Saved before icon arrives")
            vm.setUrl(url)
            vm.onUrlFocusLost()
            val id = vm.state.value.editor!!.id
            assertTrue(withContext(Dispatchers.IO) { iconRequested.await(5, TimeUnit.SECONDS) })
            assertTrue(vm.state.value.editor!!.loading)

            vm.save()
            val repository = application.container.repository
            val initial = withTimeout(5_000) {
                repository.bookmarks.first { items -> items.any { it.id == id } }.single { it.id == id }
            }
            awaitEditorClosed(vm)
            assertEquals(IconSourceMode.AUTO, initial.selectedIconSource)
            assertNull(initial.autoImagePath)
            assertEquals(1, initial.imageVersion)
            assertEquals(1L, allowIconResponse.count)

            allowIconResponse.countDown()
            val completed = withTimeout(5_000) {
                repository.bookmarks.first { items -> items.any { it.id == id && it.autoImagePath != null } }
                    .single { it.id == id }
            }
            assertEquals(url, completed.url)
            assertEquals("Saved before icon arrives", completed.title)
            assertEquals(IconSourceMode.AUTO, completed.selectedIconSource)
            assertTrue(completed.imageVersion > initial.imageVersion)
            assertNotNull(completed.lastFetchedAt)
            assertTrue(completed.sourceUrl!!.endsWith("/icon.png"))
            assertEquals(Color.GREEN, application.container.iconStore.read(completed.autoImagePath)!!.getPixel(256, 256))
            assertNull(vm.state.value.editor)
        } finally {
            allowIconResponse.countDown()
            server.shutdown()
        }
    }

    @Test fun `bulk refresh finishing after a custom save cannot replace the user image`() = runBlocking {
        val server = MockWebServer()
        val iconRequested = CountDownLatch(1)
        val allowIconResponse = CountDownLatch(1)
        val greenPng = ByteArrayOutputStream().also { bitmap(Color.GREEN).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> MockResponse().setHeader("Content-Type", "text/html")
                    .setBody("""<head><link rel="icon" sizes="192x192" href="/icon.png"></head>""")
                "/icon.png" -> {
                    iconRequested.countDown()
                    if (allowIconResponse.await(5, TimeUnit.SECONDS)) {
                        MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(greenPng))
                    } else MockResponse().setResponseCode(503)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val original = seedBookmark(IconSourceMode.AUTO, server.url("/site").toString())
            val vm = createViewModel()
            vm.refreshAll()
            assertTrue(withContext(Dispatchers.IO) { iconRequested.await(5, TimeUnit.SECONDS) })
            vm.edit(original)
            vm.setMode(IconSourceMode.CUSTOM)
            vm.save()
            awaitEditorClosed(vm)
            allowIconResponse.countDown()
            withTimeout(5_000) { vm.state.first { !it.refreshing } }
            val saved = application.container.repository.find(original.id)!!
            assertEquals(IconSourceMode.CUSTOM, saved.selectedIconSource)
            assertEquals(original.customImagePath, saved.customImagePath)
            assertEquals(original.autoImagePath, saved.autoImagePath)
            assertEquals(2, saved.imageVersion)
            assertEquals(Color.BLUE, application.container.iconStore.read(saved.customImagePath)!!.getPixel(256, 256))
        } finally {
            allowIconResponse.countDown()
            server.shutdown()
        }
    }

    private suspend fun createViewModel(handle: SavedStateHandle = SavedStateHandle()): MainViewModel {
        val store = ViewModelStore().also(stores::add)
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application, handle) as T
        })
        return provider[MainViewModel::class.java].also { vm ->
            withTimeout(5_000) { vm.state.first { !it.loading } }
        }
    }

    private suspend fun awaitEditorClosed(vm: MainViewModel) {
        withTimeout(5_000) { vm.state.first { it.editor == null } }
    }

    private suspend fun seedBookmark(mode: IconSourceMode, url: String = "https://example.com/"): BookmarkItem {
        val container = application.container
        val item = BookmarkItem(title = "Example", url = url, selectedIconSource = mode, imageVersion = 1,
            sourceUrl = "https://example.com/old.png", lastFetchedAt = 1234L, createdAt = 1L, updatedAt = 1L)
        val automatic = container.iconStore.save(item.id, 1, IconSourceMode.AUTO, bitmap(Color.RED))
        val custom = container.iconStore.save(item.id, 1, IconSourceMode.CUSTOM, bitmap(Color.BLUE))
        return item.copy(autoImagePath = automatic, customImagePath = custom).also { container.repository.save(it) }
    }

    private fun bitmap(color: Int): Bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}
