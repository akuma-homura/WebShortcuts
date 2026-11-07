package io.github.homura251.webshortcuts.data

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class LibraryOrderingTest {
    private val a = BookmarkItem(id = "a", title = "alpha", url = "https://a.test/", createdAt = 10, updatedAt = 30)
    private val b = a.copy(id = "b", title = "ALPHA", createdAt = 20, updatedAt = 10)
    private val c = a.copy(id = "c", title = "Beta", createdAt = 20, updatedAt = 20)
    private val library = LibrarySnapshot(listOf(a, b, c))
    private fun sorted(value: LibrarySortPreferences) = LibraryOrdering.bookmarks(library, "", TagFilter(), value, Locale.US).map { it.id }

    @Test fun defaultIsNewestFirstAndTiesKeepManualOrderInBothDirections() {
        assertEquals(listOf("b", "c", "a"), sorted(LibrarySortPreferences()))
        assertEquals(listOf("a", "b", "c"), sorted(LibrarySortPreferences(bookmarkDirection = SortDirection.ASCENDING)))
        assertEquals(listOf(a, b, c), library.bookmarks)
    }

    @Test fun namesIgnoreCaseAndUpdatedTimeHasItsOwnOrder() {
        assertEquals(listOf("c", "a", "b"), sorted(LibrarySortPreferences(bookmarks = BookmarkSortField.TITLE)))
        assertEquals(listOf("a", "b", "c"), sorted(LibrarySortPreferences(bookmarks = BookmarkSortField.TITLE,
            bookmarkDirection = SortDirection.ASCENDING)))
        assertEquals(listOf("a", "c", "b"), sorted(LibrarySortPreferences(bookmarks = BookmarkSortField.UPDATED_AT)))
        assertEquals(listOf("b", "c", "a"), sorted(LibrarySortPreferences(bookmarks = BookmarkSortField.UPDATED_AT,
            bookmarkDirection = SortDirection.ASCENDING)))
    }

    @Test fun chineseNamesUseLocaleCollationAndManualSortStillAppliesSearchAndIntersection() {
        val tags = listOf(BookmarkTag("work", "Work"), BookmarkTag("docs", "Docs"))
        val items = listOf(a.copy(title = "中", tagIds = setOf("work")), b.copy(title = "阿", tagIds = setOf("work", "docs")),
            c.copy(title = "波", tagIds = setOf("work", "docs")))
        val snapshot = LibrarySnapshot(items, tags)
        assertEquals(listOf("b", "c", "a"), LibraryOrdering.bookmarks(snapshot, "", TagFilter(),
            LibrarySortPreferences(bookmarks = BookmarkSortField.TITLE, bookmarkDirection = SortDirection.ASCENDING), Locale.CHINA).map { it.id })
        assertEquals(listOf("b", "c"), LibraryOrdering.bookmarks(snapshot, "Docs", TagFilter(setOf("work", "docs")),
            LibrarySortPreferences(bookmarks = BookmarkSortField.MANUAL)).map { it.id })
    }

    @Test fun tagCountsUseTheWholeLibraryAndEmptyTagsRemainSortable() {
        val tags = listOf(BookmarkTag("one", "Zulu"), BookmarkTag("two", "alpha"), BookmarkTag("empty", "Empty"))
        val snapshot = LibrarySnapshot(listOf(a.copy(tagIds = setOf("two")), b.copy(tagIds = setOf("one", "two"))), tags)
        assertEquals(listOf("two", "empty", "one"), LibraryOrdering.tags(snapshot, LibrarySortPreferences(), Locale.US).map { it.id })
        assertEquals(listOf("two", "one", "empty"), LibraryOrdering.tags(snapshot,
            LibrarySortPreferences(tags = TagSortField.COUNT, tagDirection = SortDirection.DESCENDING)).map { it.id })
        assertEquals(listOf("empty", "one", "two"), LibraryOrdering.tags(snapshot,
            LibrarySortPreferences(tags = TagSortField.COUNT)).map { it.id })
        assertEquals(tags, LibraryOrdering.tags(snapshot, LibrarySortPreferences(tags = TagSortField.MANUAL)))
    }

    @Test fun filteredReorderOnlyReplacesMatchingSlots() {
        val ids = listOf("hidden-0", "a", "hidden-1", "b", "c", "hidden-2")
        assertEquals(listOf("hidden-0", "c", "hidden-1", "a", "b", "hidden-2"),
            LibraryOrdering.reorder(ids, { it }, ids, listOf("c", "a", "b")))
    }

    @Test fun duplicateUnknownAndStaleOrdersAreRejected() {
        val ids = listOf("a", "b", "c")
        assertTrue(runCatching { LibraryOrdering.reorder(ids, { it }, ids, listOf("a", "a")) }.isFailure)
        assertTrue(runCatching { LibraryOrdering.reorder(ids, { it }, ids, listOf("missing")) }.isFailure)
        assertTrue(runCatching { LibraryOrdering.reorder(ids, { it }, ids, emptyList()) }.isFailure)
        assertTrue(runCatching { LibraryOrdering.reorder(ids, { it }, ids.reversed(), ids) }.exceptionOrNull() is OrderConflictException)
    }
}
