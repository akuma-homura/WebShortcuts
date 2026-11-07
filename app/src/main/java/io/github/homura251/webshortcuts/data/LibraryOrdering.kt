package io.github.homura251.webshortcuts.data

import java.text.Collator
import java.util.Locale

enum class BookmarkSortField { CREATED_AT, UPDATED_AT, TITLE, MANUAL }
enum class TagSortField { NAME, COUNT, MANUAL }
enum class SortDirection { ASCENDING, DESCENDING }

data class LibrarySortPreferences(
    val bookmarks: BookmarkSortField = BookmarkSortField.CREATED_AT,
    val bookmarkDirection: SortDirection = SortDirection.DESCENDING,
    val tags: TagSortField = TagSortField.NAME,
    val tagDirection: SortDirection = SortDirection.ASCENDING,
)

class OrderConflictException : IllegalStateException("收藏或标签已发生变化，请重新进入整理")

/** Display sorting never mutates the stored, global manual order. */
object LibraryOrdering {
    fun bookmarks(library: LibrarySnapshot, query: String, filter: TagFilter,
                  preferences: LibrarySortPreferences, locale: Locale = Locale.getDefault()): List<BookmarkItem> {
        val items = LibraryTags.filter(library.bookmarks, library.tags, query, filter)
        if (preferences.bookmarks == BookmarkSortField.MANUAL) return items
        val names = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        // sortedWith is stable: equal values retain their manual positions in either direction.
        return items.sortedWith { a, b ->
            val comparison = when (preferences.bookmarks) {
                BookmarkSortField.CREATED_AT -> a.createdAt.compareTo(b.createdAt)
                BookmarkSortField.UPDATED_AT -> a.updatedAt.compareTo(b.updatedAt)
                BookmarkSortField.TITLE -> names.compare(a.title, b.title)
                BookmarkSortField.MANUAL -> 0
            }
            directed(comparison, preferences.bookmarkDirection)
        }
    }

    fun tags(library: LibrarySnapshot, preferences: LibrarySortPreferences,
             locale: Locale = Locale.getDefault()): List<BookmarkTag> {
        if (preferences.tags == TagSortField.MANUAL) return library.tags
        val counts = tagCounts(library.bookmarks)
        val names = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return library.tags.sortedWith { a, b ->
            directed(if (preferences.tags == TagSortField.COUNT) (counts[a.id] ?: 0).compareTo(counts[b.id] ?: 0)
                else names.compare(a.name, b.name), preferences.tagDirection)
        }
    }

    fun tagCounts(items: List<BookmarkItem>): Map<String, Int> = buildMap {
        items.forEach { item -> item.tagIds.forEach { id -> put(id, (get(id) ?: 0) + 1) } }
    }

    /** Replace only the visible slots, resolving IDs against the latest records under the repo lock. */
    fun <T> reorder(items: List<T>, id: (T) -> String, baseOrder: List<String>, orderedScope: List<String>): List<T> {
        if (items.map(id) != baseOrder) throw OrderConflictException()
        require(orderedScope.isNotEmpty() && orderedScope.distinct().size == orderedScope.size &&
            baseOrder.toSet().containsAll(orderedScope)) { "整理顺序无效，请重新操作" }
        val byId = items.associateBy(id)
        val scope = orderedScope.toSet()
        val replacement = orderedScope.iterator()
        return items.map { if (id(it) in scope) byId.getValue(replacement.next()) else it }
    }

    private fun directed(value: Int, direction: SortDirection): Int =
        if (direction == SortDirection.ASCENDING) value.compareTo(0) else 0.compareTo(value)
}
