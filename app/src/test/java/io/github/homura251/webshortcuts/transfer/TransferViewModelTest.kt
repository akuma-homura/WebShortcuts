package io.github.homura251.webshortcuts.transfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.MainViewModel
import io.github.homura251.webshortcuts.WebShortcutsApplication
import io.github.homura251.webshortcuts.data.BookmarkItem
import java.io.File
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
class TransferViewModelTest {
    private lateinit var application: WebShortcutsApplication
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        application.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        application.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @After fun tearDown() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }

    @Test fun preparedImportAndUserChoicesSurviveViewModelRecreation() = runBlocking {
        val saved = SavedStateHandle()
        val (first, owner) = vm(saved)
        assertTrue(first.open())
        await(first, TransferPhase.IDLE)
        first.onImportPicked(Uri.fromFile(html()))
        await(first, TransferPhase.PREVIEW)
        first.setMode(ImportMode.REPLACE)
        val snapshot = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        owner.clear()
        val (restored, _) = vm(snapshot)
        await(restored, TransferPhase.PREVIEW)
        assertEquals(ImportMode.REPLACE, restored.state.value.mode)
        assertFalse(restored.state.value.restoreAppearance)
        assertEquals(1, restored.state.value.preview!!.itemCount)
        restored.commitImport()
        await(restored, TransferPhase.RESULT)
        assertEquals("Imported", application.container.repository.snapshot().single().title)
        assertNull(snapshot.get<String>("transfer.token"))
        assertEquals(TransferPhase.RESULT.name, snapshot.get<String>("transfer.phase"))
    }

    @Test fun cancellingFilePickersMakesNoChangesAndClosingReleasesTheCollectionLease() = runBlocking {
        val (vm, _) = vm()
        vm.open(); await(vm, TransferPhase.IDLE)
        vm.importPickerLaunched()
        vm.onImportPicked(null)
        await(vm, TransferPhase.IDLE)
        vm.prepareExport(TransferFormat.BACKUP_ZIP)
        await(vm, TransferPhase.EXPORT_READY)
        vm.exportPickerLaunched()
        vm.onExportPicked(null)
        await(vm, TransferPhase.IDLE)
        assertTrue(application.container.repository.snapshot().isEmpty())
        vm.close()
        withTimeout(5000) { vm.state.first { !it.visible } }
        assertFalse(application.container.transferGate.busy.value)
        assertTrue(File(application.cacheDir, "transfers").listFiles().orEmpty().isEmpty())
    }

    @Test fun collectionEditsAreBlockedWhileTheTransferSessionOwnsTheLease() = runBlocking {
        val main = MainViewModel(application, SavedStateHandle())
        ViewModelStore().also { it.put("main", main); stores += it }
        withTimeout(5000) { main.state.first { !it.loading } }
        val (vm, _) = vm()
        vm.open(); await(vm, TransferPhase.IDLE)
        main.add("https://example.com/", "Blocked")
        assertNull(main.state.value.editor)
        vm.close()
        withTimeout(5000) { vm.state.first { !it.visible } }
        main.add()
        assertNotNull(main.state.value.editor)
        main.dismissEditor()
    }

    @Test fun expiredPreparedFilesFailWithoutPretendingAnImportSucceeded() = runBlocking {
        val saved = SavedStateHandle(mapOf("transfer.visible" to true, "transfer.phase" to TransferPhase.PREVIEW.name,
            "transfer.token" to "a1c686c2-ac87-412f-a9de-33cb4fe966db"))
        val (vm, _) = vm(saved)
        await(vm, TransferPhase.ERROR)
        assertTrue(application.container.repository.snapshot().isEmpty())
        vm.restart(); await(vm, TransferPhase.IDLE)
    }

    @Test fun queuedDeletionCannotRunAfterAnImportSessionHasAlreadyFinished() = runBlocking {
        val main = MainViewModel(application, SavedStateHandle())
        ViewModelStore().also { it.put("main", main); stores += it }
        withTimeout(5000) { main.state.first { !it.loading } }
        val item = BookmarkItem(title = "Restored entry", url = "https://example.test/")
        application.container.repository.save(item)
        val mutex = application.container.mutationMutex
        mutex.lock()
        try {
            main.delete(item)
            val owner = application.container.transferGate.begin()!!
            application.container.transferGate.end(owner)
        } finally { mutex.unlock() }
        withTimeout(5000) { main.messages.first { it.contains("删除失败") } }
        assertEquals(item, application.container.repository.find(item.id))
    }

    @Test fun systemContractsUseScopedDocumentsAndCancellationReturnsNoUri() {
        val open = TransferOpenDocument()
        val choose = open.createIntent(application, arrayOf("*/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, choose.action)
        assertTrue(choose.hasCategory(Intent.CATEGORY_OPENABLE))
        assertNull(open.parseResult(Activity.RESULT_CANCELED, Intent().setData(Uri.parse("content://ignored/backup"))))
        for (format in TransferFormat.entries) {
            val create = ActivityResultContracts.CreateDocument(format.mimeType)
            val intent = create.createIntent(application, "backup.${format.extension}")
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
            assertEquals(format.mimeType, intent.type)
            assertEquals("backup.${format.extension}", intent.getStringExtra(Intent.EXTRA_TITLE))
            assertNull(create.parseResult(Activity.RESULT_CANCELED, null))
        }
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle()): Pair<TransferViewModel, ViewModelStore> {
        val store = ViewModelStore().also(stores::add)
        val provider = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = TransferViewModel(application, handle) as T
        })
        return provider[TransferViewModel::class.java] to store
    }

    private suspend fun await(vm: TransferViewModel, phase: TransferPhase) {
        withTimeout(5000) { vm.state.first { it.phase == phase } }
    }

    private fun html() = File(application.cacheDir, "input.html").apply {
        writeText("<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><DT><A HREF='https://example.test/'>Imported</A></DL>")
    }
}
