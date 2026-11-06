package io.github.homura251.webshortcuts.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.MainActivity
import io.github.homura251.webshortcuts.MainViewModel
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.WebShortcutsApplication
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkRepository
import io.github.homura251.webshortcuts.data.IconSourceMode
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the actual Activity -> editor -> ViewModel -> disk pipeline, without icon network I/O. */
class BookmarkLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as WebShortcutsApplication).container
    private val token = UUID.randomUUID().toString().take(8)
    private val originalTitle = "流程验证 $token"
    private val editedTitle = "已编辑 $token"
    private val originalUrl = "https://example.com/bookmark-$token"
    private val editedUrl = "https://example.net/updated-$token?from=shortcut#entry"
    private var fixtureId: String? = null

    @Test
    fun generatedBookmarkSurvivesRecreationEditsInPlaceAndDeletesFromDisk() {
        var viewModel = currentViewModel()
        compose.waitUntil(timeoutMillis = 10_000) { !viewModel.state.value.loading }
        assertNull("The repository must be readable before exercising writes", container.repository.loadError.value)
        val initialIds = container.repository.bookmarks.value.map { it.id }.toSet()
        val addLabel = if (initialIds.isEmpty()) R.string.add_first_site else R.string.add_site
        compose.onNodeWithText(context.getString(addLabel)).assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.editor != null }

        // Choose GENERATED before entering an address: this flow never contacts either example host.
        compose.onNodeWithText(context.getString(R.string.mode_generated)).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.editor?.mode == IconSourceMode.GENERATED }
        replaceField(R.string.url_label, originalUrl)
        replaceField(R.string.title_label, originalTitle)
        compose.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            viewModel.state.value.editor == null && container.repository.bookmarks.value.any { it.title == originalTitle }
        }
        val created = container.repository.bookmarks.value.single { it.title == originalTitle && it.url == originalUrl }
        fixtureId = created.id
        assertEquals(IconSourceMode.GENERATED, created.selectedIconSource)
        assertEquals(initialIds + created.id, container.repository.bookmarks.value.map { it.id }.toSet())
        assertPersisted(created.id, originalTitle, originalUrl)

        compose.activityRule.scenario.recreate()
        viewModel = currentViewModel()
        compose.waitUntil(timeoutMillis = 10_000) {
            !viewModel.state.value.loading && viewModel.state.value.bookmarks.any { it.id == created.id }
        }
        compose.onNodeWithText(originalTitle).assertIsDisplayed()
        openMenu(originalTitle)
        compose.onNodeWithText(context.getString(R.string.edit_bookmark)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.editor?.id == created.id }
        assertEquals(IconSourceMode.GENERATED, viewModel.state.value.editor?.mode)
        replaceField(R.string.url_label, editedUrl)
        replaceField(R.string.title_label, editedTitle)

        // Send a real system Back event to the full-screen dialog after closing the IME.
        // Cancelling the discard prompt must keep both draft fields and leave disk unchanged.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.onNodeWithText(context.getString(R.string.discard_title)).assertIsDisplayed()
        assertPersisted(created.id, originalTitle, originalUrl)
        compose.onNodeWithText(context.getString(R.string.keep_editing)).performClick()
        compose.runOnIdle {
            assertEquals(editedTitle, viewModel.state.value.editor?.title)
            assertEquals(editedUrl, viewModel.state.value.editor?.url)
        }
        compose.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            viewModel.state.value.editor == null && container.repository.bookmarks.value.any {
                it.id == created.id && it.title == editedTitle && it.url == editedUrl
            }
        }
        assertEquals(initialIds + created.id, container.repository.bookmarks.value.map { it.id }.toSet())
        assertEquals(1, container.repository.bookmarks.value.count { it.id == created.id })
        assertTrue(container.repository.bookmarks.value.none { it.title == originalTitle })
        assertPersisted(created.id, editedTitle, editedUrl)

        openMenu(editedTitle)
        compose.onNodeWithText(context.getString(R.string.delete_bookmark)).performClick()
        compose.onNodeWithText(context.getString(R.string.delete_title, editedTitle)).assertIsDisplayed()
        assertNotNull("Showing confirmation must not delete a bookmark", persisted(created.id))
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            container.repository.bookmarks.value.none { it.id == created.id }
        }
        assertNull("Deletion must reach disk", persisted(created.id))
        assertEquals(initialIds, container.repository.bookmarks.value.map { it.id }.toSet())

        compose.activityRule.scenario.recreate()
        viewModel = currentViewModel()
        compose.waitUntil(timeoutMillis = 10_000) { !viewModel.state.value.loading }
        compose.onNodeWithText(editedTitle).assertDoesNotExist()
        assertTrue(viewModel.state.value.bookmarks.none { it.id == created.id })
    }

    @After
    fun removeOnlyThisTestsFixture() {
        // Keep the emulator reusable if an assertion failed midway; never clear the entire collection.
        val viewModel = currentViewModel()
        runBlocking {
            withTimeout(10_000) { viewModel.state.first { it.editor?.saving != true } }
        }
        compose.activityRule.scenario.onActivity { viewModel.dismissEditor() }
        runBlocking {
            container.mutationMutex.withLock {
                val ownIds = container.repository.bookmarks.value.filter {
                    it.id == fixtureId ||
                        (it.title == originalTitle && it.url == originalUrl) ||
                        (it.title == editedTitle && it.url == editedUrl)
                }.map { it.id }
                ownIds.forEach { container.repository.delete(it) }
            }
        }
    }

    private fun currentViewModel(): MainViewModel {
        lateinit var viewModel: MainViewModel
        compose.activityRule.scenario.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        }
        return viewModel
    }

    private fun replaceField(label: Int, value: String) {
        compose.onNode(hasSetTextAction() and hasText(context.getString(label)))
            .performScrollTo().performClick().performTextReplacement(value)
    }

    private fun openMenu(title: String) {
        compose.onNodeWithContentDescription(context.getString(R.string.site_options, title)).performClick()
    }

    private fun persisted(id: String): BookmarkItem? = runBlocking {
        val independentReader = BookmarkRepository(context)
        independentReader.load()
        assertNull(independentReader.loadError.value)
        independentReader.find(id)
    }

    private fun assertPersisted(id: String, title: String, url: String) {
        val item = checkNotNull(persisted(id)) { "Bookmark $id was not persisted" }
        assertEquals(id, item.id)
        assertEquals(title, item.title)
        assertEquals(url, item.url)
        assertEquals(IconSourceMode.GENERATED, item.selectedIconSource)
    }
}
