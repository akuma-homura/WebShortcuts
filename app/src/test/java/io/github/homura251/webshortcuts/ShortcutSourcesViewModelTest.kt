package io.github.homura251.webshortcuts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.ui.SourceFilter
import java.util.UUID
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = WebShortcutsApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutSourcesViewModelTest {
    private lateinit var application: WebShortcutsApplication
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun prepare() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        application.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        application.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
    @After fun finish() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun `changing only the browser is saved and resetting to automatic is saved`() = runBlocking {
        val item = BookmarkItem(title = "Work", url = "https://example.com/", selectedIconSource = IconSourceMode.GENERATED)
        application.container.repository.save(item)
        val vm = viewModel()
        vm.edit(item)
        vm.setBrowser("com.chrome.beta")
        vm.save()
        awaitClosed(vm)
        val updated = application.container.repository.find(item.id)!!
        assertEquals("com.chrome.beta", updated.browserPackage)
        assertEquals(item.id, updated.id)
        vm.edit(updated)
        vm.setBrowser(null)
        vm.save()
        awaitClosed(vm)
        assertNull(application.container.repository.find(item.id)!!.browserPackage)
    }

    @Test fun `browser selection survives editor recreation and saving clears draft keys`() = runBlocking {
        val handle = SavedStateHandle(mapOf("draft.id" to UUID.randomUUID().toString(), "draft.new" to true,
            "draft.title" to "Personal", "draft.url" to "https://example.com/", "draft.mode" to "GENERATED",
            "draft.browser" to "org.mozilla.firefox", "draft.sourceType" to "WEB", "draft.dirty" to true))
        val vm = viewModel(handle)
        assertEquals("org.mozilla.firefox", vm.state.value.editor!!.browserPackage)
        val id = vm.state.value.editor!!.id
        vm.save()
        awaitClosed(vm)
        assertEquals("org.mozilla.firefox", application.container.repository.find(id)!!.browserPackage)
        assertFalse(handle.contains("draft.browser"))
        assertFalse(handle.contains("draft.sourceType"))
    }

    @Test fun `local draft saves without a URL or source URI and never enables automatic network icons`() = runBlocking {
        val fileName = application.container.localPageStore.restoreBytes("offline.html",
            "<!doctype html><html><body>离线文档</body></html>".toByteArray())
        val id = UUID.randomUUID().toString()
        val handle = SavedStateHandle(mapOf("draft.id" to id, "draft.new" to true, "draft.title" to "离线文档",
            "draft.url" to "", "draft.mode" to "GENERATED", "draft.sourceType" to "HTML",
            "draft.localFile" to fileName, "draft.localName" to "我的文档.html", "draft.dirty" to true))
        val vm = viewModel(handle)
        assertTrue(vm.state.value.editor!!.isLocal)
        vm.setMode(IconSourceMode.AUTO)
        vm.setBrowser("com.android.chrome")
        vm.onUrlFocusLost()
        assertEquals(IconSourceMode.GENERATED, vm.state.value.editor!!.mode)
        assertNull(vm.state.value.editor!!.browserPackage)
        assertFalse(vm.state.value.editor!!.loading)
        vm.save()
        awaitClosed(vm)
        val saved = application.container.repository.find(id)!!
        assertEquals(BookmarkSourceType.HTML, saved.sourceType)
        assertEquals("", saved.url)
        assertEquals(fileName, saved.localFileName)
        assertNotNull(application.container.localPageStore.resolve(fileName))
        assertFalse(handle.contains("draft.localFile"))
    }

    @Test fun `missing local draft cannot create a broken bookmark`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val vm = viewModel(SavedStateHandle(mapOf("draft.id" to id, "draft.new" to true, "draft.title" to "Missing",
            "draft.mode" to "GENERATED", "draft.sourceType" to "HTML", "draft.localFile" to "missing.html")))
        vm.save()
        assertNotNull(vm.state.value.editor!!.error)
        assertNull(application.container.repository.find(id))
    }

    @Test fun `source filter selects only visible local items and deleting releases the saved file`() = runBlocking {
        val fileName = application.container.localPageStore.restoreBytes("offline.html", "<html><body>Offline</body></html>".toByteArray())
        val local = BookmarkItem(title = "Offline", url = "", selectedIconSource = IconSourceMode.GENERATED,
            sourceType = BookmarkSourceType.HTML, localFileName = fileName, localDisplayName = "offline.html")
        val web = BookmarkItem(title = "Online", url = "https://example.com/", selectedIconSource = IconSourceMode.GENERATED)
        application.container.repository.save(local)
        application.container.repository.save(web)
        val vm = viewModel()
        vm.setSourceFilter(SourceFilter.LOCAL)
        vm.select(local)
        vm.selectAll()
        assertEquals(setOf(local.id), vm.state.value.selectedIds)
        vm.cancelSelection()
        vm.delete(local)
        withTimeout(5000) { vm.state.first { state -> state.bookmarks.none { it.id == local.id } } }
        // Repository publication precedes cleanup; acquire the same mutex to observe completion.
        application.container.mutationMutex.lock()
        try { assertNull(application.container.localPageStore.resolve(fileName)) }
        finally { application.container.mutationMutex.unlock() }
        assertNotNull(application.container.repository.find(web.id))
    }

    private suspend fun viewModel(handle: SavedStateHandle = SavedStateHandle()): MainViewModel {
        val store = ViewModelStore().also(stores::add)
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application, handle) as T
        })
        return provider[MainViewModel::class.java].also { vm -> withTimeout(5000) { vm.state.first { !it.loading } } }
    }
    private suspend fun awaitClosed(vm: MainViewModel) { withTimeout(5000) { vm.state.first { it.editor == null } } }
}
