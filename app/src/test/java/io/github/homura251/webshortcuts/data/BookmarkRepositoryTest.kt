package io.github.homura251.webshortcuts.data

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BookmarkRepositoryTest {
    private lateinit var context: Context
    private lateinit var metadata: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        metadata = File(context.filesDir, "bookmarks.json")
        listOf(metadata, File(metadata.path + ".bak"), File(metadata.path + ".new")).forEach { it.delete() }
    }

    @Test fun `save update reload and delete preserve stable identity and custom mode`() = runTest {
        val repository = BookmarkRepository(context)
        val initial = BookmarkItem(title = "Example", url = "example.com", createdAt = 10, updatedAt = 10)
        repository.save(initial)
        val changed = initial.copy(title = "文档", url = "https://example.org/", selectedIconSource = IconSourceMode.CUSTOM,
            customImagePath = "icons/custom/${initial.id}_v2.webp", imageVersion = 2, updatedAt = 20)
        repository.save(changed)
        assertEquals(1, repository.bookmarks.value.size)
        val reopened = BookmarkRepository(context)
        assertEquals(changed, reopened.find(initial.id))
        assertEquals(changed, reopened.delete(initial.id))
        assertNull(reopened.find(initial.id))
        assertNull(reopened.delete(initial.id))
        assertEquals(emptyList<BookmarkItem>(), BookmarkCodec.decode(metadata.readText()))
    }

    @Test fun `concurrent writes serialize without losing entries`() = runTest {
        val repository = BookmarkRepository(context)
        val bookmarks = (0 until 32).map { BookmarkItem(title = "Site $it", url = "https://example.com/$it") }
        bookmarks.map { item -> async { repository.save(item) } }.awaitAll()
        assertEquals(bookmarks.map { it.id }.toSet(), repository.bookmarks.value.map { it.id }.toSet())
        val reopened = BookmarkRepository(context)
        reopened.load()
        assertEquals(32, reopened.bookmarks.value.size)
    }

    @Test fun `corrupt data remains intact and write is rejected rather than overwriting with empty state`() = runTest {
        val original = "{broken JSON with important data"
        metadata.writeText(original)
        val repository = BookmarkRepository(context)
        repository.load()
        assertNotNull(repository.loadError.value)
        val failure = runCatching { repository.save(BookmarkItem(title = "New", url = "https://example.com/")) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(original, metadata.readText())
        assertTrue(repository.bookmarks.value.isEmpty())
    }

    @Test fun `interrupted atomic write restores the previously committed collection`() = runTest {
        val original = BookmarkItem(title = "Saved", url = "https://example.com/", createdAt = 10, updatedAt = 10)
        BookmarkRepository(context).save(original)
        val interrupted = AtomicFile(metadata).startWrite()
        interrupted.write("{partial".toByteArray())
        interrupted.close() // Process died before finishWrite.
        val reopened = BookmarkRepository(context)
        assertEquals(original, reopened.find(original.id))
        assertNull(reopened.loadError.value)
    }

    @Test fun `explicit reload recovers after the original file is repaired`() = runTest {
        metadata.writeText("invalid")
        val repository = BookmarkRepository(context)
        repository.load()
        assertNotNull(repository.loadError.value)
        metadata.writeText(BookmarkCodec.encode(emptyList()))
        repository.load()
        assertNull(repository.loadError.value)
        repository.save(BookmarkItem(title = "Recovered", url = "https://example.com/"))
        assertEquals(1, repository.bookmarks.value.size)
    }
}
