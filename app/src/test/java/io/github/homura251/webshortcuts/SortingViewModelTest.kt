package io.github.homura251.webshortcuts

import androidx.lifecycle.*
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.*
import io.github.homura251.webshortcuts.ui.OrderTarget
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = WebShortcutsApplication::class)
class SortingViewModelTest {
    private lateinit var application: WebShortcutsApplication
    private val stores = mutableListOf<ViewModelStore>()
    private val repo get() = application.container.repository
    private val tags = listOf(BookmarkTag("work", "Work"), BookmarkTag("docs", "Docs"), BookmarkTag("extra", "Extra"))
    private val items = (0..4).map { BookmarkItem(id = "site-$it", title = "Site $it", url = "https://site$it.test/",
        selectedIconSource = IconSourceMode.GENERATED, tagIds = if (it % 2 == 0) setOf("work", "docs") else emptySet(),
        updatedAt = 10, imageVersion = 2) }
    private val manual = LibrarySortPreferences(bookmarks = BookmarkSortField.MANUAL, tags = TagSortField.MANUAL)

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        application.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        application.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        application.container.sortPreferences.update(LibrarySortPreferences())
    }
    @After fun teardown() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }

    @Test fun automaticSortCannotBeOrganizedAndManualModeBlocksCompetingActions() = runBlocking {
        seed()
        val vm = vm()
        vm.beginReorder(OrderTarget.BOOKMARKS)
        assertNull(vm.state.value.reorder)
        vm.setSorting(manual)
        vm.beginReorder(OrderTarget.BOOKMARKS)
        assertNotNull(vm.state.value.reorder)
        vm.setQuery("blocked"); vm.setTagFilter(TagFilter(untagged = true))
        vm.setSorting(LibrarySortPreferences()); vm.add(); vm.select(items[0]); vm.createTag("Blocked"); vm.refreshAll()
        assertEquals("", vm.state.value.query)
        assertEquals(TagFilter(), vm.state.value.tagFilter)
        assertEquals(manual, vm.state.value.sorting)
        assertNull(vm.state.value.editor)
        assertFalse(vm.state.value.selectionMode)
        assertFalse(vm.state.value.refreshing)
        assertEquals(tags, repo.library.value.tags)
        vm.cancelReorder()
        assertNull(vm.state.value.reorder)
    }

    @Test fun filteredReorderPreservesHiddenSlotsAndConcurrentImageMetadata() = runBlocking {
        seed()
        val vm = vm()
        vm.setSorting(manual)
        vm.setQuery("Site"); vm.setTagFilter(TagFilter(setOf("work", "docs")))
        vm.beginReorder(OrderTarget.BOOKMARKS)
        assertEquals(listOf("site-0", "site-2", "site-4"), vm.state.value.reorder!!.orderedIds)
        vm.moveReorder("site-4", 0)
        val latest = items[2].copy(updatedAt = 99, imageVersion = 3)
        repo.save(latest)
        vm.saveReorder()
        await { vm.state.value.reorder == null }
        assertEquals(listOf(items[4], items[1], items[0], items[3], latest), repo.library.value.bookmarks)
        assertEquals("Site", vm.state.value.query)
        assertEquals(setOf("work", "docs"), vm.state.value.tagFilter.ids)
    }

    @Test fun cancellationLeavesDiskUntouchedAndNewItemsKeepTheirExistingInsertionPolicy() = runBlocking {
        seed()
        val vm = vm()
        vm.setSorting(manual); vm.beginReorder(OrderTarget.TAGS)
        vm.moveReorder("extra", 0); vm.cancelReorder()
        assertEquals(tags, repo.library.value.tags)
        repo.save(items[0].copy(id = "new", title = "New"))
        repo.createTag("New tag")
        assertEquals("new", repo.library.value.bookmarks.first().id)
        assertEquals("New tag", repo.library.value.tags.last().name)
    }

    @Test fun processRecreationRestoresOnlyValidDraftIds() = runBlocking {
        seed()
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.setSorting(manual); first.beginReorder(OrderTarget.TAGS, "o")
        first.moveReorder("docs", 0)
        val expected = first.state.value.reorder!!.orderedIds
        stores.last().clear()
        val restored = vm(saved)
        assertEquals(expected, restored.state.value.reorder!!.orderedIds)
        restored.saveReorder()
        await { restored.state.value.reorder == null }
        assertEquals(listOf(tags[1], tags[0], tags[2]), repo.library.value.tags)
    }

    @Test fun structuralChangesAndTransfersInvalidateDrafts() = runBlocking {
        seed()
        val vm = vm()
        vm.setSorting(manual); vm.beginReorder(OrderTarget.BOOKMARKS)
        repo.delete(items[0].id)
        await { vm.state.value.reorder == null }
        vm.beginReorder(OrderTarget.BOOKMARKS)
        val lease = application.container.transferGate.begin()!!
        await { vm.state.value.reorder == null }
        application.container.transferGate.end(lease)
        assertEquals(items.drop(1), repo.library.value.bookmarks)
    }

    @Test fun failedCommitKeepsDraftForRetryAndSavingCannotBeCancelledOrDoubleSubmitted() = runBlocking {
        seed()
        val vm = vm()
        vm.setSorting(manual); vm.beginReorder(OrderTarget.BOOKMARKS); vm.moveReorder("site-4", 0)
        val blocker = File(application.filesDir, "bookmarks.json.new").apply { mkdirs() }
        try {
            vm.saveReorder()
            await { vm.state.value.reorder?.error != null }
            assertEquals(items, repo.library.value.bookmarks)
            assertEquals("site-4", vm.state.value.reorder!!.orderedIds.first())
        } finally { blocker.deleteRecursively() }
        application.container.mutationMutex.lock()
        vm.saveReorder(); vm.saveReorder(); vm.cancelReorder()
        assertTrue(vm.state.value.reorder!!.saving)
        application.container.mutationMutex.unlock()
        await { vm.state.value.reorder == null }
        assertEquals(listOf(items[4]) + items.take(4), repo.library.value.bookmarks)
    }

    @Test fun selectionLocksSortingAndQueuedSaveCannotCrossATransfer() = runBlocking {
        seed()
        val vm = vm()
        vm.select(items[0]); vm.setSorting(manual)
        assertEquals(LibrarySortPreferences(), vm.state.value.sorting)
        vm.cancelSelection(); vm.setSorting(manual); vm.beginReorder(OrderTarget.BOOKMARKS); vm.moveReorder("site-4", 0)
        application.container.mutationMutex.lock()
        try {
            vm.saveReorder()
            val lease = application.container.transferGate.begin()!!
            application.container.transferGate.end(lease)
        } finally { application.container.mutationMutex.unlock() }
        await { vm.state.value.reorder == null }
        assertEquals(items, repo.library.value.bookmarks)
    }

    private suspend fun seed() = repo.replaceAll(LibrarySnapshot(items, tags))
    private suspend fun vm(saved: SavedStateHandle = SavedStateHandle()): MainViewModel {
        val store = ViewModelStore().also(stores::add)
        val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application, saved) as T
        })[MainViewModel::class.java]
        withTimeout(5000) { vm.state.first { !it.loading } }
        return vm
    }
    private suspend fun await(predicate: () -> Boolean) {
        withTimeout(5000) { while (!predicate()) yield() }
    }
}
