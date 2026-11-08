package io.github.homura251.webshortcuts.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OrderingRepositoryTest {
    private lateinit var context: Context
    private lateinit var repo: BookmarkRepository
    private val tags = listOf(BookmarkTag("one", "One"), BookmarkTag("two", "Two"), BookmarkTag("three", "Three"))
    private val items = (0..4).map { BookmarkItem(id = "item-$it", title = "Site $it", url = "https://site$it.test/",
        updatedAt = 12, imageVersion = 8, tagIds = setOf(tags[it % 3].id)) }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.getSharedPreferences("sorting", Context.MODE_PRIVATE).edit().clear().commit()
        repo = BookmarkRepository(context)
    }

    @Test fun filteredReorderPreservesLatestFieldsAndPersistsGlobalOrder() = runBlocking {
        repo.replaceAll(LibrarySnapshot(items, tags))
        val base = items.map { it.id }
        val latest = items[1].copy(title = "Refreshed", imageVersion = 9, updatedAt = 34, autoImagePath = "icons/auto/new.png")
        repo.save(latest)
        repo.reorderBookmarks(base, listOf(items[3].id, items[1].id))
        assertEquals(listOf(items[0], items[3], items[2], latest, items[4]), repo.library.value.bookmarks)
        repo.reorderTags(tags.map { it.id }, listOf("three", "one"))
        assertEquals(listOf(tags[2], tags[1], tags[0]), repo.library.value.tags)
        val reopened = BookmarkRepository(context)
        reopened.load()
        assertEquals(repo.library.value, reopened.library.value)
    }

    @Test fun failedWriteAndStaleBaselineLeaveMetadataUntouched() = runBlocking {
        val original = LibrarySnapshot(items, tags)
        repo.replaceAll(original)
        val metadata = File(context.filesDir, "bookmarks.json")
        val bytes = metadata.readBytes()
        val blocker = File(metadata.path + ".new").apply { mkdirs() }
        try {
            assertTrue(runCatching { repo.reorderBookmarks(items.map { it.id }, items.reversed().map { it.id }) }.isFailure)
            assertTrue(runCatching { repo.reorderTags(tags.map { it.id }, tags.reversed().map { it.id }) }.isFailure)
            assertEquals(original, repo.library.value)
            assertArrayEquals(bytes, metadata.readBytes())
        } finally { blocker.deleteRecursively() }
        repo.delete(items[0].id)
        val afterDelete = repo.library.value
        assertTrue(runCatching { repo.reorderBookmarks(items.map { it.id }, items.reversed().map { it.id }) }.exceptionOrNull() is OrderConflictException)
        assertEquals(afterDelete, repo.library.value)
    }

    @Test fun preferencesSurviveRestartWithoutTouchingManualOrderAndUnknownValuesFallBack() = runBlocking {
        repo.replaceAll(LibrarySnapshot(items, tags))
        val prefs = SortPreferences(context)
        val changed = LibrarySortPreferences(BookmarkSortField.TITLE, SortDirection.DESCENDING, TagSortField.COUNT, SortDirection.DESCENDING)
        prefs.update(changed)
        assertEquals(changed, SortPreferences(context).state.value)
        assertEquals(items, repo.library.value.bookmarks)
        context.getSharedPreferences("sorting", Context.MODE_PRIVATE).edit().putInt("bookmarks", 8)
            .putString("bookmark_direction", "future").putString("tags", "future").remove("tag_direction").commit()
        assertEquals(LibrarySortPreferences(), SortPreferences(context).state.value)
    }
}
